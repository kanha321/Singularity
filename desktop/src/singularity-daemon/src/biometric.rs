use base64::engine::general_purpose::STANDARD as BASE64;
use base64::Engine;
use tokio::io::{AsyncBufReadExt, AsyncWriteExt, BufReader};
use tokio::process::Command;
use crate::error::{DaemonError, Result};
use crate::transport::AdbTunnel;
use singularity_protocol::AuthProgressEvent;

pub async fn authenticate<F>(
    adb_address: &str, reason: &str, timeout_secs: u32, mut on_event: F,
) -> Result<(bool, u32, Option<String>)>
where F: FnMut(AuthProgressEvent) + Send {
    let _ = Command::new("adb").args(["-s", adb_address, "shell", "am", "start-foreground-service", "-n", "com.kanhaji.singularity/.service.AppBridgeService"]).status().await;
    let stream = AdbTunnel::connect_unix(adb_address, "singularity_app").await?;
    let (read_half, mut write_half) = stream.into_split();
    let mut reader = BufReader::new(read_half);

    let action_type = crate::auth_cache::get_action_type(reason);
    let mut nonce_bytes = [0u8; 32];
    rand::RngCore::fill_bytes(&mut rand::thread_rng(), &mut nonce_bytes);
    let nonce_b64 = BASE64.encode(&nonce_bytes);
    let now_secs = std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).unwrap_or_default().as_secs();

    let request = serde_json::json!({
        "action": "authenticate", "reason": reason, "auth_action": action_type,
        "action_type": action_type, "timeout_secs": timeout_secs, "nonce": nonce_b64, "timestamp": now_secs,
    });
    let mut req_line = serde_json::to_string(&request).map_err(|e| DaemonError::Protocol(e.into()))?;
    req_line.push('\n');
    write_half.write_all(req_line.as_bytes()).await?;
    write_half.flush().await?;

    let mut resp_line = String::new();
    loop {
        resp_line.clear();
        if reader.read_line(&mut resp_line).await? == 0 {
            return Err(DaemonError::Io(std::io::Error::new(std::io::ErrorKind::UnexpectedEof, "Connection closed")));
        }
        let trimmed = resp_line.trim();
        if trimmed.is_empty() { continue; }
        let val: serde_json::Value = serde_json::from_str(trimmed).map_err(|e| DaemonError::Protocol(e.into()))?;

        if let Some(event) = val.get("event").and_then(|e| e.as_str()) {
            match event {
                "auth_ready" => on_event(AuthProgressEvent::Ready { device_name: val.get("device_name").and_then(|d| d.as_str()).map(|s| s.to_string()) }),
                "finger_mismatch" => on_event(AuthProgressEvent::FingerMismatch { attempt: val.get("attempt").and_then(|a| a.as_u64()).unwrap_or(1) as u32 }),
                "sensor_lockout" => on_event(AuthProgressEvent::SensorLockout { duration_secs: val.get("duration_secs").and_then(|d| d.as_u64()).unwrap_or(30) as u32, message: val.get("message").and_then(|m| m.as_str()).unwrap_or("Lockout").to_string() }),
                "cancelled" => on_event(AuthProgressEvent::Cancelled),
                _ => {}
            }
            continue;
        }

        if let Some(status) = val.get("status").and_then(|s| s.as_str()) {
            let success = status == "success";
            let failed = val.get("failed_attempts").and_then(|a| a.as_u64()).unwrap_or(0) as u32;
            let reason_msg = val.get("reason").and_then(|r| r.as_str()).map(|s| s.to_string());
            if !success { return Ok((false, failed, reason_msg)); }

            let dev_id = val.get("device_id").and_then(|d| d.as_str()).unwrap_or(adb_address);
            let dev_name = val.get("device_name").and_then(|d| d.as_str()).unwrap_or("Android");
            if let Some(pk) = val.get("public_key").and_then(|p| p.as_str()) {
                if crate::transport::pairing::get_authorized_device(dev_id).is_none() {
                    let _ = crate::transport::pairing::save_authorized_device(crate::transport::pairing::AuthorizedDevice {
                        device_id: dev_id.to_string(), device_name: dev_name.to_string(), model: val.get("model").and_then(|m| m.as_str()).map(|s| s.to_string()),
                        public_key: pk.to_string(), last_counter: 0, created_at: now_secs, revoked: false,
                    });
                }
            }

            if let Some(auth_dev) = crate::transport::pairing::get_authorized_device(dev_id) {
                if let Some(sig) = val.get("signature").and_then(|s| s.as_str()) {
                    let counter = val.get("counter").and_then(|c| c.as_u64()).unwrap_or(0);
                    let user = reason.split(':').nth(1).unwrap_or("").trim();
                    let payload = crate::crypto::build_canonical_payload(&nonce_bytes, now_secs, counter, action_type, user);
                    if counter <= auth_dev.last_counter && auth_dev.last_counter > 0 {
                        return Ok((false, failed, Some("Replay attack detected".into())));
                    }
                    if crate::crypto::verify_signature(&auth_dev.public_key, &payload, sig).is_err() {
                        return Ok((false, failed, Some("Signature verification failed".into())));
                    }
                    crate::transport::pairing::update_counter(dev_id, counter);
                } else {
                    return Ok((false, failed, Some("Cryptographic signature required".into())));
                }
            }
            return Ok((true, failed, reason_msg));
        }
    }
}

pub async fn cancel(adb_address: &str) -> Result<()> {
    let stream = AdbTunnel::connect_unix(adb_address, "singularity_app").await?;
    let (_, mut write_half) = stream.into_split();
    let mut req_line = serde_json::to_string(&serde_json::json!({ "action": "cancel_auth" }))
        .map_err(|e| DaemonError::Protocol(e.into()))?;
    req_line.push('\n');
    write_half.write_all(req_line.as_bytes()).await?;
    write_half.flush().await?;
    Ok(())
}

#[cfg(test)]
mod tests {
    #[test]
    fn parse_auth_status_matches() {
        let v: serde_json::Value = serde_json::from_str(r#"{"status":"success"}"#).unwrap();
        assert_eq!(v.get("status").and_then(|s| s.as_str()), Some("success"));
    }
}
