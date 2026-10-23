use std::sync::Arc;
use std::time::{Duration, Instant};
use tokio::process::Command;
use tokio::sync::RwLock;

/// Parses `adb devices` stdout to extract all currently connected and authorized device serials.
pub fn parse_adb_devices(stdout: &str) -> Vec<String> {
    stdout
        .lines()
        .filter_map(|line| {
            let mut parts = line.split_whitespace();
            let serial = parts.next()?;
            (parts.next()? == "device").then(|| serial.to_string())
        })
        .collect()
}

/// Parses `/proc/net/route` text to find the default gateway IPv4 address (Destination == 00000000).
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

/// Reads `/proc/net/route` from the Linux kernel to get the active network gateway IP.
pub fn get_default_gateway() -> Option<String> {
    std::fs::read_to_string("/proc/net/route")
        .ok()
        .and_then(|c| parse_proc_net_route(&c))
}

/// Attempts to run `adb connect <addr>` and returns true if adb connected or was already connected.
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

/// Resolves an active, ready-to-use ADB device serial.
/// If `configured` is `"auto"`, it detects connected devices or discovers the gateway via ADB TCP.
pub async fn resolve_active_device(configured: &str) -> Option<String> {
    let output = Command::new("adb").arg("devices").output().await.ok()?;
    let stdout = String::from_utf8_lossy(&output.stdout);
    let mut devices = parse_adb_devices(&stdout);

    if configured != "auto" {
        if devices.iter().any(|d| d == configured) {
            return Some(configured.to_string());
        }
        if configured.contains(':') && try_adb_connect(configured).await {
            return Some(configured.to_string());
        }
    }

    if let Some(dev) = devices.first() {
        return Some(dev.clone());
    }

    if let Some(gateway) = get_default_gateway() {
        let candidate = format!("{}:5555", gateway);
        if try_adb_connect(&candidate).await {
            if let Ok(out) = Command::new("adb").arg("devices").output().await {
                let stdout = String::from_utf8_lossy(&out.stdout);
                devices = parse_adb_devices(&stdout);
                if devices.iter().any(|d| d == &candidate) {
                    return Some(candidate);
                }
            }
        }
    }

    None
}

/// Resolves an active device, polling every 400ms for up to `max_wait_secs` if still `"auto"`.
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
        let sample = "List of devices attached\n10.196.94.46:5555\tdevice\nemulator-5554\toffline\n23049PCD8I\tunauthorized\n";
        let devs = parse_adb_devices(sample);
        assert_eq!(devs, vec!["10.196.94.46:5555"]);
    }

    #[test]
    fn parse_proc_net_route_extracts_gateway_ip() {
        let sample = "Iface\tDestination\tGateway\tFlags\tRefCnt\tUse\tMetric\tMask\tMTU\tWindow\tIRTT\nwlo1\t00000000\t2E5EC40A\t0003\t0\t0\t100\t00000000\t0\t0\t0\nwlo1\t005EC40A\t00000000\t0001\t0\t0\t100\t00FFFFFF\t0\t0\t0\n";
        let gw = parse_proc_net_route(sample);
        assert_eq!(gw, Some("10.196.94.46".to_string()));
    }

    #[test]
    fn parse_proc_net_route_returns_none_when_no_default_gateway() {
        let sample = "Iface\tDestination\tGateway\tFlags\tRefCnt\tUse\tMetric\tMask\tMTU\tWindow\tIRTT\nwlo1\t005EC40A\t00000000\t0001\t0\t0\t100\t00FFFFFF\t0\t0\t0\n";
        let gw = parse_proc_net_route(sample);
        assert_eq!(gw, None);
    }

    #[tokio::test]
    async fn resolve_or_wait_device_returns_existing_non_auto() {
        let lock = Arc::new(RwLock::new("192.168.1.50:5555".to_string()));
        let res = resolve_or_wait_device(&lock, 1).await;
        assert_eq!(res, Some("192.168.1.50:5555".to_string()));
    }
}
