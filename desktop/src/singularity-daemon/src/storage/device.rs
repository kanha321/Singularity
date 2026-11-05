use tokio::process::Command;
use crate::error::{DaemonError, Result};

pub async fn ensure_device_storage_ready(serial: &str) -> Result<()> {
    let trimmed = serial.trim();
    if trimmed.is_empty() || trimmed == "auto" {
        return Err(DaemonError::Storage("No valid device serial provided".into()));
    }

    let state = Command::new("adb")
        .args(["-s", trimmed, "get-state"])
        .output()
        .await
        .map_err(|e| DaemonError::Storage(format!("Failed to verify device state via ADB: {}", e)))?;

    if !state.status.success() {
        return Err(DaemonError::Storage(format!(
            "Device {} is not in valid state for ADB storage mount",
            trimmed
        )));
    }

    let state_str = String::from_utf8_lossy(&state.stdout);
    if !state_str.trim().eq_ignore_ascii_case("device") {
        return Err(DaemonError::Storage(format!(
            "Device {} is in state '{}', expected 'device'",
            trimmed,
            state_str.trim()
        )));
    }

    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn ensure_device_storage_ready_fails_on_bogus_serial() {
        let res = ensure_device_storage_ready("127.0.0.1:59999").await;
        assert!(res.is_err());
    }

    #[tokio::test]
    async fn ensure_device_storage_ready_fails_on_empty_serial() {
        let res = ensure_device_storage_ready("").await;
        assert!(res.is_err());
    }
}
