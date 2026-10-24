use std::path::PathBuf;
use std::sync::Arc;
use tokio::io::{AsyncReadExt, AsyncWriteExt};
use tokio::net::UnixStream;
use tokio::sync::broadcast::Sender;
use tokio::sync::{Mutex, RwLock};
use singularity_core::FeatureRegistry;
use singularity_protocol::{IpcRequest, IpcResponse};
use crate::auth_cache::SharedAuthCache;
use crate::error::{DaemonError, Result};
use crate::storage;
use crate::transport::AdbTunnel;

pub async fn handle_client(
    mut stream: UnixStream,
    clip_tx: Sender<String>,
    registry: Arc<RwLock<FeatureRegistry>>,
    active_device: Arc<RwLock<String>>,
    storage_tunnel: Arc<Mutex<Option<AdbTunnel>>>,
    active_mount: Arc<Mutex<Option<PathBuf>>>,
    has_root: Arc<std::sync::atomic::AtomicBool>,
    auth_cache: SharedAuthCache,
) -> Result<()> {
    let mut len_buf = [0u8; 4];
    stream.read_exact(&mut len_buf).await?;
    let len = u32::from_be_bytes(len_buf) as usize;

    let mut buf = vec![0u8; len];
    stream.read_exact(&mut buf).await?;

    let request: IpcRequest = serde_json::from_slice(&buf)
        .map_err(|e| DaemonError::Protocol(e.into()))?;

    let response = match request {
        IpcRequest::GetStatus => {
            let statuses = registry.read().await.statuses();
            IpcResponse::Status(statuses)
        }
        IpcRequest::SetClipboard { text } => {
            tracing::info!(target: "ipc_server", len = text.len(), "Forwarding clip to highway via broadcast");
            let _ = clip_tx.send(text);
            IpcResponse::Ok
        }
        IpcRequest::MountStorage { remote_path } => {
            let is_root = has_root.load(std::sync::atomic::Ordering::SeqCst);
            let default_path = singularity_core::features::storage::config::default_remote_path(is_root);
            let raw_remote = remote_path.unwrap_or_else(|| default_path.to_string());
            let target_remote = singularity_core::features::storage::config::canonical_remote_path(&raw_remote);
            let mount_point = PathBuf::from("/mnt/phone");
            let identity_file = dirs::config_dir()
                .unwrap_or_else(|| PathBuf::from("/tmp"))
                .join("singularity/storage_id_ed25519");

            let adb_address = crate::transport::resolve_or_wait_device(&active_device, 2).await
                .unwrap_or_else(|| active_device.try_read().map(|g| g.clone()).unwrap_or_else(|_| "auto".into()));

            match AdbTunnel::forward_tcp(&adb_address, 2222).await {
                Ok(tunnel) => {
                    let port = tunnel.local_port();
                    *storage_tunnel.lock().await = Some(tunnel);
                    match storage::mount_phone(port, &target_remote, &mount_point, &identity_file) {
                        Ok(()) => {
                            *active_mount.lock().await = Some(mount_point.clone());
                            IpcResponse::StorageMounted {
                                mount_point: mount_point.to_string_lossy().to_string(),
                            }
                        }
                        Err(e) => {
                            *storage_tunnel.lock().await = None;
                            IpcResponse::Error(format!("Failed to mount storage: {}", e))
                        }
                    }
                }
                Err(e) => IpcResponse::Error(format!("Failed to forward ADB port for storage: {}", e)),
            }
        }
        IpcRequest::UnmountStorage => {
            if let Some(mount_point) = active_mount.lock().await.take() {
                let _ = storage::unmount_phone(&mount_point);
            }
            *storage_tunnel.lock().await = None;
            IpcResponse::StorageUnmounted
        }
        IpcRequest::AcceptCall => {
            tracing::info!(target: "ipc_server", "Accepting call via ShellBridge");
            IpcResponse::Ok
        }
        IpcRequest::EndCall => {
            tracing::info!(target: "ipc_server", "Ending call via ShellBridge");
            IpcResponse::Ok
        }
        IpcRequest::AuthenticateBiometric { reason, timeout_secs } => {
            crate::server::auth_handler::handle_biometric(
                &mut stream,
                reason,
                timeout_secs,
                &auth_cache,
                &active_device,
            ).await?
        }
        IpcRequest::CancelBiometric => {
            tracing::info!(target: "ipc_server", "Processing cancel biometric request");
            let dev = active_device.read().await.clone();
            if dev != "auto" {
                let _ = crate::biometric::cancel(&dev).await;
            }
            IpcResponse::Ok
        }
        IpcRequest::Custom { .. } => IpcResponse::Ok,
    };

    let resp_bytes = serde_json::to_vec(&response).map_err(|e| DaemonError::Protocol(e.into()))?;
    stream.write_all(&(resp_bytes.len() as u32).to_be_bytes()).await?;
    stream.write_all(&resp_bytes).await?;
    stream.flush().await?;

    Ok(())
}
