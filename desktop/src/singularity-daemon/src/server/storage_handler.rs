use std::path::PathBuf;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use tokio::sync::{Mutex, RwLock};
use singularity_protocol::IpcResponse;
use crate::storage;
use crate::transport::AdbTunnel;

pub async fn handle_mount_storage(
    active_device: &Arc<RwLock<String>>,
    has_root: &Arc<AtomicBool>,
    active_mounts: &Arc<Mutex<Vec<PathBuf>>>,
    remote_path: Option<String>,
) -> IpcResponse {
    let is_root = has_root.load(Ordering::SeqCst);
    let adb_address = match crate::transport::resolve_or_wait_device(active_device, 3).await {
        Some(addr) => addr,
        None => return IpcResponse::Error("No Android device detected on network".into()),
    };

    if let Some(custom) = remote_path {
        let target_remote = singularity_core::features::storage::config::canonical_remote_path(&custom);
        if target_remote == "/" && !is_root {
            return IpcResponse::Error("Device is not rooted (KernelSU/su required to mount root /)".into());
        }
        let mount_point = if target_remote == "/" {
            PathBuf::from("/mnt/root")
        } else {
            PathBuf::from("/mnt/phone")
        };
        if let Err(e) = storage::ensure_device_storage_ready(&adb_address).await {
            return IpcResponse::Error(format!("Failed to prepare phone storage daemon: {}", e));
        }
        match storage::mount_phone(&adb_address, &target_remote, &mount_point) {
            Ok(()) => {
                active_mounts.lock().await.push(mount_point.clone());
                IpcResponse::StorageMounted { mount_point: mount_point.to_string_lossy().to_string() }
            }
            Err(e) => IpcResponse::Error(format!("Failed to mount storage: {}", e)),
        }
    } else {
        match storage::mount_phone_storage(&adb_address, false, is_root, Arc::clone(active_mounts)).await {
            Ok(points) => IpcResponse::StorageMounted { mount_point: points },
            Err(e) => IpcResponse::Error(format!("Failed to mount storage: {}", e)),
        }
    }
}

pub async fn handle_unmount_storage(
    active_mounts: &Arc<Mutex<Vec<PathBuf>>>,
    storage_tunnel: &Arc<Mutex<Option<AdbTunnel>>>,
) -> IpcResponse {
    let mounts = active_mounts.lock().await.drain(..).collect::<Vec<_>>();
    for mount_point in mounts {
        let _ = storage::unmount_phone(&mount_point);
    }
    *storage_tunnel.lock().await = None;
    IpcResponse::StorageUnmounted
}

