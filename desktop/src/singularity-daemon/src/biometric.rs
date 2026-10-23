use tokio::io::{AsyncBufReadExt, AsyncWriteExt, BufReader};
use tokio::process::Command;
use crate::error::{DaemonError, Result};
use crate::transport::AdbTunnel;

pub async fn authenticate(adb_address: &str, reason: &str, timeout_secs: u32) -> Result<bool> {
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

    let request = serde_json::json!({
        "action": "authenticate",
        "reason": reason,
        "timeout_secs": timeout_secs,
    });
    let mut req_line = serde_json::to_string(&request)
        .map_err(|e| DaemonError::Protocol(e.into()))?;
    req_line.push('\n');

    write_half.write_all(req_line.as_bytes()).await?;
    write_half.flush().await?;

    let mut resp_line = String::new();
    reader.read_line(&mut resp_line).await?;

    tracing::info!(target: "biometric", resp = %resp_line.trim(), "AppBridge biometric response received");

    let val: serde_json::Value = serde_json::from_str(&resp_line)
        .map_err(|e| DaemonError::Protocol(e.into()))?;

    Ok(val.get("status").and_then(|s| s.as_str()) == Some("success"))
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
