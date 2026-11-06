use std::sync::Arc;
use tokio::process::Command;
use tokio::sync::RwLock;
use singularity_protocol::{DeviceEntry, IpcResponse};
use crate::transport::{discovery, pairing};

fn parse_adb_devices_l(stdout: &str, active_serial: &str) -> Vec<DeviceEntry> {
    let mut entries = Vec::new();
    for line in stdout.lines().skip(1) {
        let parts: Vec<&str> = line.split_whitespace().collect();
        if parts.len() >= 2 && parts[1] == "device" {
            let serial = parts[0].to_string();
            let model = parts.iter()
                .find(|p| p.starts_with("model:"))
                .and_then(|p| p.strip_prefix("model:"))
                .map(|s| s.to_string());
            let connection_type = if serial.contains(':') { "wifi".into() } else { "usb".into() };
            let is_active = serial == active_serial;
            let is_paired = pairing::is_device_paired(&serial, model.as_deref());
            entries.push(DeviceEntry {
                serial,
                model,
                connection_type,
                is_active,
                is_paired,
            });
        }
    }
    entries
}

pub async fn handle_list_devices(active_device: &Arc<RwLock<String>>) -> IpcResponse {
    let current_active = active_device.read().await.clone();
    let output = match Command::new("adb").args(["devices", "-l"]).output().await {
        Ok(o) => String::from_utf8_lossy(&o.stdout).to_string(),
        Err(e) => return IpcResponse::Error(format!("Failed running adb devices: {}", e)),
    };

    let entries = parse_adb_devices_l(&output, &current_active);
    IpcResponse::Devices(entries)
}

pub async fn handle_select_device(active_device: &Arc<RwLock<String>>, serial: &str) -> IpcResponse {
    let trimmed = serial.trim();
    if trimmed.is_empty() {
        return IpcResponse::Error("Device serial cannot be empty".into());
    }
    *active_device.write().await = trimmed.to_string();
    pairing::set_preferred_device(trimmed);
    discovery::save_last_known_device(trimmed);
    tracing::info!(target: "device_handler", serial = %trimmed, "Active device switched and set as preferred");
    IpcResponse::Ok
}

pub async fn handle_pair_device(serial: &str) -> IpcResponse {
    let trimmed = serial.trim();
    if trimmed.is_empty() {
        return IpcResponse::Error("Device serial cannot be empty".into());
    }
    pairing::add_paired_device(trimmed);
    tracing::info!(target: "device_handler", serial = %trimmed, "Device added to paired whitelist");
    IpcResponse::Ok
}

pub async fn handle_unpair_device(serial: &str) -> IpcResponse {
    let trimmed = serial.trim();
    if trimmed.is_empty() {
        return IpcResponse::Error("Device serial cannot be empty".into());
    }
    pairing::remove_paired_device(trimmed);
    tracing::info!(target: "device_handler", serial = %trimmed, "Device removed from paired whitelist");
    IpcResponse::Ok
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parse_adb_devices_l_extracts_model_and_type() {
        let sample = "List of devices attached\n10.33.3.107:5555 device product:marble model:23049PCD8I\n";
        let entries = parse_adb_devices_l(sample, "10.33.3.107:5555");
        assert_eq!(entries.len(), 1);
        assert_eq!(entries[0].serial, "10.33.3.107:5555");
        assert_eq!(entries[0].model.as_deref(), Some("23049PCD8I"));
        assert_eq!(entries[0].connection_type, "wifi");
        assert!(entries[0].is_active);
    }
}

