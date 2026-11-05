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

#[allow(clippy::too_many_arguments)]
pub async fn handle_client(
    mut stream: UnixStream,
    clip_tx: Sender<String>,
    registry: Arc<RwLock<FeatureRegistry>>,
    active_device: Arc<RwLock<String>>,
    storage_tunnel: Arc<Mutex<Option<AdbTunnel>>>,
    active_mounts: Arc<Mutex<Vec<PathBuf>>>,
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
            if let Some(adb_address) = crate::transport::resolve_or_wait_device(&active_device, 3).await {
                if let Some(custom) = remote_path {
                    let target_remote = singularity_core::features::storage::config::canonical_remote_path(&custom);
                    if target_remote == "/" && !is_root {
                        IpcResponse::Error("Device is not rooted (KernelSU/su required to mount root /)".into())
                    } else {
                        let mount_point = if target_remote == "/" {
                            PathBuf::from("/mnt/root")
                        } else {
                            PathBuf::from("/mnt/phone")
                        };
                        if let Err(e) = storage::ensure_device_storage_ready(&adb_address).await {
                            IpcResponse::Error(format!("Failed to prepare phone storage daemon: {}", e))
                        } else {
                            match storage::mount_phone(&adb_address, &target_remote, &mount_point) {
                                Ok(()) => {
                                    active_mounts.lock().await.push(mount_point.clone());
                                    IpcResponse::StorageMounted { mount_point: mount_point.to_string_lossy().to_string() }
                                }
                                Err(e) => IpcResponse::Error(format!("Failed to mount storage: {}", e)),
                            }
                        }
                    }
                } else {
                    match storage::mount_phone_storage(&adb_address, false, is_root, Arc::clone(&active_mounts)).await {
                        Ok(points) => IpcResponse::StorageMounted { mount_point: points },
                        Err(e) => IpcResponse::Error(format!("Failed to mount storage: {}", e)),
                    }
                }
            } else {
                IpcResponse::Error("No Android device detected on network".into())
            }
        }
        IpcRequest::UnmountStorage => {
            let mounts = active_mounts.lock().await.drain(..).collect::<Vec<_>>();
            for mount_point in mounts {
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
        IpcRequest::OpenUrl { url } => {
            match crate::transport::resolve_or_wait_device(&active_device, 2).await {
                Some(addr) => {
                    let runner = Arc::new(crate::transport::AdbShellRunner::new(&addr));
                    let shell = singularity_core::bridge::ShellBridge::with_runner(runner);
                    match singularity_core::features::browser::launch_url_on_device(&shell, &url).await {
                        Ok(_) => IpcResponse::Ok,
                        Err(e) => IpcResponse::Error(e.to_string()),
                    }
                }
                None => IpcResponse::Error("No Android device detected on network".into()),
            }
        }
        IpcRequest::RemountSystem { read_write } => {
            crate::server::remount_handler::handle_remount(&active_device, &has_root, read_write).await
        }
        IpcRequest::Custom { .. } => IpcResponse::Ok,
    };

    let resp_bytes = serde_json::to_vec(&response).map_err(|e| DaemonError::Protocol(e.into()))?;
    stream.write_all(&(resp_bytes.len() as u32).to_be_bytes()).await?;
    stream.write_all(&resp_bytes).await?;
    stream.flush().await?;

    Ok(())
}
