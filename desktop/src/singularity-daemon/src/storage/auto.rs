use std::path::PathBuf;
use std::sync::Arc;
use tokio::sync::Mutex;
use crate::error::Result;
use crate::storage;
use crate::transport::AdbTunnel;

pub async fn mount_phone_storage(
    serial: &str,
    mount_root: bool,
    has_root: bool,
    active_mounts: Arc<Mutex<Vec<PathBuf>>>,
) -> Result<String> {
    storage::ensure_device_storage_ready(serial).await?;

    let phone_mount = PathBuf::from("/mnt/phone");
    storage::mount_phone(serial, "/sdcard", &phone_mount)?;

    let mut mounted_list = vec![phone_mount.clone()];
    if mount_root && has_root {
        let root_mount = PathBuf::from("/mnt/root");
        match storage::mount_phone(serial, "/", &root_mount) {
            Ok(()) => mounted_list.push(root_mount),
            Err(e) => tracing::warn!(target: "storage_auto", error = %e, "Failed mounting / to /mnt/root"),
        }
    }

    let mut guard = active_mounts.lock().await;
    *guard = mounted_list;

    let res = guard
        .iter()
        .map(|p| p.to_string_lossy().to_string())
        .collect::<Vec<_>>()
        .join(", ");
    tracing::info!(target: "storage_auto", mounted = %res, "Phone storage successfully mounted over ADB");
    Ok(res)
}

pub async fn auto_mount_phone_storage(
    serial: &str,
    has_root: bool,
    auto_mount_enabled: bool,
    active_mounts: Arc<Mutex<Vec<PathBuf>>>,
    _storage_tunnel: Arc<Mutex<Option<AdbTunnel>>>,
) -> Result<bool> {
    if !auto_mount_enabled {
        tracing::debug!(target: "storage_auto", "Storage auto-mount disabled by configuration");
        return Ok(false);
    }

    let serial = serial.trim();
    if serial.is_empty() || serial == "auto" {
        tracing::warn!(target: "storage_auto", "No resolved device serial available for auto-mount");
        return Ok(false);
    }

    {
        let guard = active_mounts.lock().await;
        if !guard.is_empty() {
            tracing::debug!(target: "storage_auto", "Storage already mounted; skipping duplicate mount");
            return Ok(true);
        }
    }

    mount_phone_storage(serial, false, has_root, active_mounts).await?;
    Ok(true)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn test_auto_mount_skipped_when_disabled() {
        let active_mounts = Arc::new(Mutex::new(Vec::new()));
        let storage_tunnel = Arc::new(Mutex::new(None));
        let res = auto_mount_phone_storage("127.0.0.1:5555", true, false, active_mounts.clone(), storage_tunnel.clone()).await;
        assert_eq!(res.unwrap(), false);
        assert!(active_mounts.lock().await.is_empty());
    }

    #[tokio::test]
    async fn test_auto_mount_skipped_when_serial_is_auto_or_empty() {
        let active_mounts = Arc::new(Mutex::new(Vec::new()));
        let storage_tunnel = Arc::new(Mutex::new(None));
        let res1 = auto_mount_phone_storage("auto", true, true, active_mounts.clone(), storage_tunnel.clone()).await;
        assert_eq!(res1.unwrap(), false);

        let res2 = auto_mount_phone_storage("", true, true, active_mounts.clone(), storage_tunnel.clone()).await;
        assert_eq!(res2.unwrap(), false);
    }

    #[tokio::test]
    async fn test_auto_mount_skipped_when_already_mounted() {
        let active_mounts = Arc::new(Mutex::new(vec![PathBuf::from("/mnt/phone")]));
        let storage_tunnel = Arc::new(Mutex::new(None));
        let res = auto_mount_phone_storage("127.0.0.1:5555", true, true, active_mounts.clone(), storage_tunnel.clone()).await;
        assert_eq!(res.unwrap(), true);
    }

    #[tokio::test]
    async fn test_auto_mount_fails_gracefully_on_unreachable_serial() {
        let active_mounts = Arc::new(Mutex::new(Vec::new()));
        let storage_tunnel = Arc::new(Mutex::new(None));
        let res = auto_mount_phone_storage("127.0.0.1:59998", false, true, active_mounts.clone(), storage_tunnel.clone()).await;
        assert!(res.is_err());
        assert!(active_mounts.lock().await.is_empty());
    }
}
