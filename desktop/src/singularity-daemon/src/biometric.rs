use tokio::io::{AsyncBufReadExt, AsyncWriteExt, BufReader};
use tokio::process::Command;
use crate::error::{DaemonError, Result};
use crate::transport::AdbTunnel;

use singularity_protocol::AuthProgressEvent;

pub async fn authenticate<F>(
    adb_address: &str,
    reason: &str,
    timeout_secs: u32,
    mut on_event: F,
) -> Result<(bool, u32, Option<String>)>
where
    F: FnMut(AuthProgressEvent) + Send,
{
    let _ = Command::new("adb")
        .args([
            "-s", adb_address,
            "shell", "am", "start-foreground-service",
            "-n", "com.kanhaji.singularity/.service.AppBridgeService",
        ])
        .status()
        .await;

    let stream = AdbTunnel::connect_unix(adb_address, "singularity_app").await?;

    let (read_half, mut write_half) = stream.into_split();
    let mut reader = BufReader::new(read_half);

    let action_type = crate::auth_cache::get_action_type(reason);
    let request = serde_json::json!({
        "action": "authenticate",
        "reason": reason,
        "auth_action": action_type,
        "action_type": action_type,
        "timeout_secs": timeout_secs,
    });
    let mut req_line = serde_json::to_string(&request)
        .map_err(|e| DaemonError::Protocol(e.into()))?;
    req_line.push('\n');

    write_half.write_all(req_line.as_bytes()).await?;
    write_half.flush().await?;

    let mut resp_line = String::new();
    loop {
        resp_line.clear();
        let bytes_read = reader.read_line(&mut resp_line).await?;
        if bytes_read == 0 {
            return Err(DaemonError::Io(std::io::Error::new(
                std::io::ErrorKind::UnexpectedEof,
                "Connection closed before auth completed",
            )));
        }
        let trimmed = resp_line.trim();
        if trimmed.is_empty() {
            continue;
        }

        tracing::info!(target: "biometric", resp = %trimmed, "AppBridge biometric message");
        let val: serde_json::Value = serde_json::from_str(trimmed)
            .map_err(|e| DaemonError::Protocol(e.into()))?;

        if let Some(event) = val.get("event").and_then(|e| e.as_str()) {
            match event {
                "auth_ready" => on_event(AuthProgressEvent::Ready),
                "finger_mismatch" => {
                    let attempt = val.get("attempt").and_then(|a| a.as_u64()).unwrap_or(1) as u32;
                    on_event(AuthProgressEvent::FingerMismatch { attempt });
                }
                "sensor_lockout" => {
                    let duration = val.get("duration_secs").and_then(|d| d.as_u64()).unwrap_or(30) as u32;
                    let msg = val.get("message").and_then(|m| m.as_str()).unwrap_or("Sensor lockout").to_string();
                    on_event(AuthProgressEvent::SensorLockout { duration_secs: duration, message: msg });
                }
                "cancelled" => on_event(AuthProgressEvent::Cancelled),
                _ => {}
            }
            continue;
        }

        if let Some(status) = val.get("status").and_then(|s| s.as_str()) {
            let success = status == "success";
            let failed_attempts = val.get("failed_attempts").and_then(|a| a.as_u64()).unwrap_or(0) as u32;
            let reason_msg = val.get("reason").and_then(|r| r.as_str()).map(|s| s.to_string());
            return Ok((success, failed_attempts, reason_msg));
        }
    }
}

pub async fn cancel(adb_address: &str) -> Result<()> {
    let stream = AdbTunnel::connect_unix(adb_address, "singularity_app").await?;

    let (read_half, mut write_half) = stream.into_split();
    let mut reader = BufReader::new(read_half);

    let request = serde_json::json!({
        "action": "cancel_auth",
    });
    let mut req_line = serde_json::to_string(&request)
        .map_err(|e| DaemonError::Protocol(e.into()))?;
    req_line.push('\n');

    write_half.write_all(req_line.as_bytes()).await?;
    write_half.flush().await?;

    let mut resp_line = String::new();
    let _ = reader.read_line(&mut resp_line).await;

    tracing::info!(target: "biometric", resp = %resp_line.trim(), "Sent cancel_auth signal to companion app");
    Ok(())
}

#[cfg(test)]
mod tests {
    #[test]
    fn parse_auth_success_detects_token() {
        let fake_json = r#"{"status":"success"}"#;
        let val: serde_json::Value = serde_json::from_str(fake_json).unwrap();
        assert_eq!(val.get("status").and_then(|s| s.as_str()), Some("success"));
    }

    #[test]
    fn parse_auth_failure_rejects_token() {
        let fake_json = r#"{"status":"rejected","reason":"cancelled"}"#;
        let val: serde_json::Value = serde_json::from_str(fake_json).unwrap();
        assert_ne!(val.get("status").and_then(|s| s.as_str()), Some("success"));
    }
}
