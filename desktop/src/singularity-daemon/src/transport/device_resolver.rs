use std::sync::Arc;
use std::time::{Duration, Instant};
use tokio::process::Command;
use tokio::sync::RwLock;

pub fn parse_adb_devices(stdout: &str) -> Vec<String> {
    stdout.lines().filter_map(|line| {
        let mut parts = line.split_whitespace();
        let serial = parts.next()?;
        (parts.next()? == "device").then(|| serial.to_string())
    }).collect()
}

pub fn parse_proc_net_route(content: &str) -> Option<String> {
    for line in content.lines().skip(1) {
        let parts: Vec<&str> = line.split_whitespace().collect();
        if parts.len() >= 3 && parts[1] == "00000000" {
            if let Ok(val) = u32::from_str_radix(parts[2], 16) {
                if val != 0 {
                    let b = val.to_le_bytes();
                    return Some(format!("{}.{}.{}.{}", b[0], b[1], b[2], b[3]));
                }
            }
        }
    }
    None
}

pub fn get_default_gateway() -> Option<String> {
    std::fs::read_to_string("/proc/net/route")
        .ok()
        .and_then(|c| parse_proc_net_route(&c))
}

pub async fn try_adb_connect(addr: &str) -> bool {
    let output = Command::new("adb")
        .args(["connect", addr])
        .output()
        .await;

    match output {
        Ok(out) => {
            let stdout = String::from_utf8_lossy(&out.stdout);
            stdout.contains("connected") || stdout.contains("already")
        }
        Err(_) => false,
    }
}

pub async fn resolve_active_device(configured: &str) -> Option<String> {
    let output = Command::new("adb").arg("devices").output().await.ok()?;
    let stdout = String::from_utf8_lossy(&output.stdout);
    super::discovery::purge_offline_devices(&stdout).await;
    let mut devices = parse_adb_devices(&stdout);

    if configured != "auto" {
        if devices.iter().any(|d| d == configured) || (configured.contains(':') && try_adb_connect(configured).await) {
            super::discovery::save_last_known_device(configured);
            return Some(configured.to_string());
        }
    }

    if let Some(preferred) = super::pairing::get_preferred_device() {
        if devices.iter().any(|d| d == &preferred) {
            super::discovery::save_last_known_device(&preferred);
            return Some(preferred);
        }
    }

    if let Some(usb) = devices.iter().find(|d| !d.contains(':') && super::pairing::is_device_paired(d, None)) {
        super::discovery::save_last_known_device(usb);
        return Some(usb.clone());
    }

    if let Some(dev) = devices.iter().find(|d| super::pairing::is_device_paired(d, None)) {
        super::discovery::save_last_known_device(dev);
        return Some(dev.clone());
    }

    if let Some(last_device) = super::discovery::get_last_known_device() {
        if try_adb_connect(&last_device).await {
            if let Ok(out) = Command::new("adb").arg("devices").output().await {
                let stdout = String::from_utf8_lossy(&out.stdout);
                devices = parse_adb_devices(&stdout);
                if devices.iter().any(|d| d == &last_device) {
                    return Some(last_device);
                }
            }
        }
    }

    if let Some(gateway) = get_default_gateway() {
        let candidate = format!("{}:5555", gateway);
        if try_adb_connect(&candidate).await {
            if let Ok(out) = Command::new("adb").arg("devices").output().await {
                let stdout = String::from_utf8_lossy(&out.stdout);
                devices = parse_adb_devices(&stdout);
                if devices.iter().any(|d| d == &candidate) {
                    super::discovery::save_last_known_device(&candidate);
                    return Some(candidate);
                }
            }
        }
    }

    None
}

pub async fn resolve_or_wait_device(active_device: &Arc<RwLock<String>>, max_wait_secs: u64) -> Option<String> {
    let current = active_device.read().await.clone();
    if current != "auto" {
        return Some(current);
    }

    let start = Instant::now();
    let max_dur = Duration::from_secs(max_wait_secs);
    while start.elapsed() < max_dur {
        if let Some(dev) = resolve_active_device("auto").await {
            *active_device.write().await = dev.clone();
            return Some(dev);
        }
        tokio::time::sleep(Duration::from_millis(400)).await;
    }
    None
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parse_adb_devices_extracts_only_connected_devices() {
        let sample = "List of devices attached\n10.196.94.46:5555\tdevice\nemulator-5554\toffline\n";
        let devs = parse_adb_devices(sample);
        assert_eq!(devs, vec!["10.196.94.46:5555"]);
    }

    #[test]
    fn parse_proc_net_route_extracts_gateway_ip() {
        let sample = "Iface\tDestination\tGateway\tFlags\nwlo1\t00000000\t2E5EC40A\t0003\n";
        let gw = parse_proc_net_route(sample);
        assert_eq!(gw, Some("10.196.94.46".to_string()));
    }
}
