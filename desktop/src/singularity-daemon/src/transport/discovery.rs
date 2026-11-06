use std::path::PathBuf;
use std::sync::Arc;
use tokio::net::UdpSocket;
use tokio::process::Command;
use tokio::sync::RwLock;

fn cache_path() -> PathBuf {
    let uid = unsafe { libc::getuid() };
    PathBuf::from(format!("/run/user/{}/singularity_last_device", uid))
}

pub fn save_last_known_device(addr: &str) {
    if addr.is_empty() || addr == "auto" {
        return;
    }
    let path = cache_path();
    let _ = std::fs::write(&path, addr);
    if let Some(config_dir) = dirs::config_dir() {
        let dir = config_dir.join("singularity");
        let _ = std::fs::create_dir_all(&dir);
        let _ = std::fs::write(dir.join("last_device"), addr);
    }
}

pub fn get_last_known_device() -> Option<String> {
    if let Ok(content) = std::fs::read_to_string(cache_path()) {
        let trimmed = content.trim();
        if !trimmed.is_empty() && trimmed != "auto" {
            return Some(trimmed.to_string());
        }
    }
    if let Some(config_dir) = dirs::config_dir() {
        let cfg_path = config_dir.join("singularity").join("last_device");
        if let Ok(content) = std::fs::read_to_string(&cfg_path) {
            let trimmed = content.trim();
            if !trimmed.is_empty() && trimmed != "auto" {
                return Some(trimmed.to_string());
            }
        }
    }
    None
}

pub async fn purge_offline_devices(stdout: &str) {
    for line in stdout.lines() {
        let mut parts = line.split_whitespace();
        if let (Some(serial), Some(status)) = (parts.next(), parts.next()) {
            if status == "offline" && serial.contains(':') {
                tracing::info!(target: "discovery", serial = %serial, "Purging stale offline ADB device");
                let _ = Command::new("adb").args(["disconnect", serial]).status().await;
            }
        }
    }
}

pub fn start_beacon_listener(active_device: Arc<RwLock<String>>) -> tokio::task::JoinHandle<()> {
    tokio::spawn(async move {
        let socket = match UdpSocket::bind("0.0.0.0:2829").await {
            Ok(s) => s,
            Err(e) => {
                tracing::warn!(target: "discovery", "Failed to bind UDP discovery beacon socket: {}", e);
                return;
            }
        };
        tracing::info!(target: "discovery", "Listening for phone UDP discovery beacons on 0.0.0.0:2829");
        let mut buf = [0u8; 1024];
        loop {
            match socket.recv_from(&mut buf).await {
                Ok((len, peer_addr)) => {
                    let text = String::from_utf8_lossy(&buf[..len]);
                    if text.contains("singularity") && text.contains("beacon") {
                        let target_addr = format!("{}:5555", peer_addr.ip());
                        let val: serde_json::Value = serde_json::from_str(&text).unwrap_or_default();
                        let model = val.get("device").and_then(|d| d.as_str());

                        if !super::pairing::is_device_paired(&target_addr, model) {
                            tracing::debug!(target: "discovery", ip = %peer_addr.ip(), model = ?model, "Ignoring beacon from unpaired device");
                            continue;
                        }

                        save_last_known_device(&target_addr);

                        let current = active_device.read().await.clone();
                        if current == "auto" {
                            if crate::transport::device_resolver::try_adb_connect(&target_addr).await {
                                tracing::info!(target: "discovery", target_addr = %target_addr, "Auto-connected from UDP beacon");
                                *active_device.write().await = target_addr;
                            }
                        }
                    }
                }
                Err(e) => {
                    tracing::warn!(target: "discovery", "Error receiving UDP beacon: {}", e);
                    tokio::time::sleep(std::time::Duration::from_millis(500)).await;
                }
            }
        }
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn cache_path_has_valid_format() {
        let path = cache_path();
        assert!(path.to_str().unwrap().contains("singularity_last_device"));
    }
}

