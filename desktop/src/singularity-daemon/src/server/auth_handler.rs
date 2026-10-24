use std::sync::Arc;
use tokio::io::AsyncWriteExt;
use tokio::net::UnixStream;
use tokio::sync::RwLock;
use singularity_protocol::{AuthProgressEvent, IpcResponse};
use crate::auth_cache::SharedAuthCache;
use crate::error::{DaemonError, Result};

pub async fn handle_biometric(
    stream: &mut UnixStream,
    reason: String,
    timeout_secs: u32,
    auth_cache: &SharedAuthCache,
    active_device: &Arc<RwLock<String>>,
) -> Result<IpcResponse> {
    tracing::info!(target: "ipc_server", %reason, timeout_secs, "Processing biometric request");
    let cache = auth_cache.read().await;
    let eff_timeout = if timeout_secs == 0 || timeout_secs == 15 {
        cache.get_effective_timeout(&reason)
    } else {
        timeout_secs
    };
    let (is_snoozed, is_denied) = (cache.is_snoozed(), cache.is_denied(&reason));
    drop(cache);

    if is_snoozed {
        tracing::info!(target: "ipc_server", "Authentication short-circuited: snooze active");
        return Ok(IpcResponse::AuthResult {
            success: false,
            reason: Some("Global snooze active".into()),
            failed_attempts: 0,
        });
    }

    if is_denied {
        tracing::info!(target: "ipc_server", %reason, "Authentication short-circuited: policy is deny");
        return Ok(IpcResponse::AuthResult {
            success: false,
            reason: Some("Action policy is deny".into()),
            failed_attempts: 0,
        });
    }

    let dev = match crate::transport::resolve_or_wait_device(active_device, 6).await {
        Some(d) => d,
        None => {
            return Ok(IpcResponse::AuthResult {
                success: false,
                reason: Some("No Android device detected on network".into()),
                failed_attempts: 0,
            });
        }
    };

    let (tx, mut rx) = tokio::sync::mpsc::unbounded_channel::<AuthProgressEvent>();
    let auth_fut = crate::biometric::authenticate(&dev, &reason, eff_timeout, move |event| {
        let _ = tx.send(event);
    });
    tokio::pin!(auth_fut);

    loop {
        tokio::select! {
            Some(progress) = rx.recv() => {
                let resp_bytes = serde_json::to_vec(&IpcResponse::AuthProgress(progress))
                    .map_err(|e| DaemonError::Protocol(e.into()))?;
                stream.write_all(&(resp_bytes.len() as u32).to_be_bytes()).await?;
                stream.write_all(&resp_bytes).await?;
                stream.flush().await?;
            }
            res = &mut auth_fut => {
                return match res {
                    Ok((success, failed_attempts, reason_msg)) => Ok(IpcResponse::AuthResult {
                        success,
                        reason: reason_msg,
                        failed_attempts,
                    }),
                    Err(e) => Ok(IpcResponse::AuthResult {
                        success: false,
                        reason: Some(format!("Biometric error: {}", e)),
                        failed_attempts: 0,
                    }),
                };
            }
        }
    }
}
