use std::path::PathBuf;
use std::sync::Arc;
use tokio::io::{AsyncReadExt, AsyncWriteExt};
use tokio::net::UnixStream;
use tokio::sync::broadcast::Sender;
use tokio::sync::{Mutex, RwLock};
use singularity_core::FeatureRegistry;
use singularity_protocol::{IpcRequest, IpcResponse};
use crate::auth_cache::SharedAuthCache;
use crate::camera::CameraSupervisor;
use crate::error::{DaemonError, Result};
use crate::server::{auth_handler, camera_handler, device_handler, remount_handler, storage_handler};
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
    camera: CameraSupervisor,
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
            storage_handler::handle_mount_storage(&active_device, &has_root, &active_mounts, remote_path).await
        }
        IpcRequest::UnmountStorage => {
            storage_handler::handle_unmount_storage(&active_mounts, &storage_tunnel).await
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
            auth_handler::handle_biometric(&mut stream, reason, timeout_secs, &auth_cache, &active_device).await?
        }
        IpcRequest::CancelBiometric => {
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
            remount_handler::handle_remount(&active_device, &has_root, read_write).await
        }
        IpcRequest::StartCamera { config } => {
            camera_handler::handle_start_camera(&active_device, &camera, config).await
        }
        IpcRequest::StopCamera => {
            camera_handler::handle_stop_camera(&camera).await
        }
        IpcRequest::SetCameraTorch { enabled } => {
            camera_handler::handle_set_torch(&active_device, &camera, enabled).await
        }
        IpcRequest::SetCameraZoom { zoom } => {
            camera_handler::handle_set_zoom(&camera, zoom).await
        }
        IpcRequest::SwitchCameraLens { lens } => {
            camera_handler::handle_switch_lens(&active_device, &camera, lens).await
        }
        IpcRequest::GetCameraStatus => {
            camera_handler::handle_get_status(&camera).await
        }
        IpcRequest::ListDevices => device_handler::handle_list_devices(&active_device).await,
        IpcRequest::SelectDevice { serial } => device_handler::handle_select_device(&active_device, &serial).await,
        IpcRequest::PairDevice { serial } => device_handler::handle_pair_device(&serial).await,
        IpcRequest::UnpairDevice { serial } => device_handler::handle_unpair_device(&serial).await,
        IpcRequest::NotificationReply { id, text } => match crate::notification::NotificationHub::get() {
            Some(hub) => { hub.send_reply(&id, &text).await; IpcResponse::Ok }
            None => IpcResponse::Error("Notification hub offline".into()),
        },
        IpcRequest::NotificationAction { id, action_id } => match crate::notification::NotificationHub::get() {
            Some(hub) => { hub.invoke_action(&id, &action_id).await; IpcResponse::Ok }
            None => IpcResponse::Error("Notification hub offline".into()),
        },
        IpcRequest::ListNotifications => match crate::notification::NotificationHub::get() {
            Some(hub) => IpcResponse::Notifications(hub.get_active_notifications().await),
            None => IpcResponse::Notifications(Vec::new()),
        },
        IpcRequest::FindDevice { ring } => super::find_handler::handle_find_device(ring, &active_device).await?,
        IpcRequest::Custom { .. } => IpcResponse::Ok,
    };

    let resp_bytes = serde_json::to_vec(&response).map_err(|e| DaemonError::Protocol(e.into()))?;
    stream.write_all(&(resp_bytes.len() as u32).to_be_bytes()).await?;
    stream.write_all(&resp_bytes).await?;
    stream.flush().await?;

    Ok(())
}
