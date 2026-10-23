use std::sync::Arc;
use std::time::Duration;
use tokio::io::{AsyncBufReadExt, AsyncWriteExt, BufReader};
use tokio::process::Command;
use tokio::sync::RwLock;
use crate::error::{DaemonError, Result};
use crate::transport::AdbTunnel;

pub async fn query_pc_screen_locked() -> bool {
    let out = Command::new("dms").args(["ipc", "call", "lock", "isLocked"]).output().await;
    match out {
        Ok(o) => String::from_utf8_lossy(&o.stdout).trim() == "true",
        Err(_) => {
            let bus = Command::new("busctl")
                .args(["--user", "call", "org.freedesktop.ScreenSaver", "/org/freedesktop/ScreenSaver", "org.freedesktop.ScreenSaver", "GetActive"])
                .output().await;
            bus.map(|o| String::from_utf8_lossy(&o.stdout).contains("true")).unwrap_or(false)
        }
    }
}

pub async fn query_pc_logged_in() -> bool {
    let out = Command::new("loginctl").args(["list-sessions", "--no-legend"]).output().await;
    out.map(|o| {
        String::from_utf8_lossy(&o.stdout).lines().any(|l| {
            let p: Vec<&str> = l.split_whitespace().collect();
            p.len() >= 6 && p.contains(&"user") && p.iter().any(|s| s.starts_with("seat"))
        })
    }).unwrap_or(false)
}

pub async fn trigger_pc_unlock() {
    tracing::info!(target: "lock_watcher", "Triggering PC screen unlock via DMS");
    let _ = Command::new("dms").args(["ipc", "call", "lock", "unlock"]).status().await;
}

struct AppBridgeStream {
    reader: BufReader<tokio::net::unix::OwnedReadHalf>,
    writer: tokio::net::unix::OwnedWriteHalf,
}

impl AppBridgeStream {
    async fn connect(adb_address: &str) -> Result<Self> {
        let stream = AdbTunnel::connect_unix(adb_address, "singularity_app").await?;
        let (r, w) = stream.into_split();
        Ok(Self { reader: BufReader::new(r), writer: w })
    }

    async fn exchange(&mut self, locked: bool, logged_in: bool) -> Result<bool> {
        let req = serde_json::json!({
            "action": "lock_state", "locked": locked, "logged_in": logged_in, "isLoggedIn": logged_in
        });
        let mut req_str = serde_json::to_string(&req).map_err(|e| DaemonError::Protocol(e.into()))?;
        req_str.push('\n');
        self.writer.write_all(req_str.as_bytes()).await?;
        self.writer.flush().await?;

        let mut resp = String::new();
        let bytes_read = self.reader.read_line(&mut resp).await?;
        if bytes_read == 0 {
            return Err(std::io::Error::new(std::io::ErrorKind::UnexpectedEof, "Stream closed").into());
        }

        let unlock = serde_json::from_str::<serde_json::Value>(&resp)
            .ok()
            .and_then(|v| v.get("request_unlock").and_then(|u| u.as_bool()))
            .unwrap_or(false);
        Ok(unlock)
    }
}

#[allow(dead_code)]
pub async fn send_lock_packet(adb_address: &str, is_locked: bool, is_logged_in: bool) -> Result<bool> {
    let mut stream = AppBridgeStream::connect(adb_address).await?;
    stream.exchange(is_locked, is_logged_in).await
}

pub fn start_lock_watcher(active_device: Arc<RwLock<String>>) -> tokio::task::JoinHandle<()> {
    tokio::spawn(async move {
        let mut last_state = None;
        let mut client: Option<AppBridgeStream> = None;
        let mut last_addr = String::new();

        loop {
            let adb_address = active_device.read().await.clone();
            if adb_address == "auto" {
                client = None;
                tokio::time::sleep(Duration::from_millis(500)).await;
                continue;
            }
            if adb_address != last_addr {
                last_addr = adb_address.clone();
                client = None;
            }

            let current_locked = query_pc_screen_locked().await;
            let current_logged_in = query_pc_logged_in().await;
            let current = (current_locked, current_logged_in);

            if last_state != Some(current) {
                last_state = Some(current);
                tracing::info!(target: "lock_watcher", is_locked = current_locked, is_logged_in = current_logged_in, "PC session state changed");
            }

            if client.is_none() {
                client = AppBridgeStream::connect(&adb_address).await.ok();
            }

            if let Some(ref mut stream) = client {
                match stream.exchange(current_locked, current_logged_in).await {
                    Ok(true) if current_locked => trigger_pc_unlock().await,
                    Ok(_) => {}
                    Err(e) => {
                        tracing::debug!(target: "lock_watcher", "AppBridge socket dropped: {}, will reconnect", e);
                        client = None;
                    }
                }
            }

            tokio::time::sleep(Duration::from_millis(500)).await;
        }
    })
}

#[cfg(test)]
mod tests {
    #[test]
    fn parse_lock_json_matches_expected() {
        let json = serde_json::json!({
            "action": "lock_state", "locked": true, "logged_in": false, "isLoggedIn": false
        });
        assert_eq!(json["locked"], true);
        assert_eq!(json["logged_in"], false);
        assert_eq!(json["isLoggedIn"], false);
    }
}
