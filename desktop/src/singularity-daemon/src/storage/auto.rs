use std::path::PathBuf;
use std::sync::Arc;
use tokio::sync::Mutex;
use crate::error::Result;
use crate::storage;
use crate::transport::AdbTunnel;

pub async fn mount_dual_storage(
    serial: &str,
    active_mounts: Arc<Mutex<Vec<PathBuf>>>,
    storage_tunnel: Arc<Mutex<Option<AdbTunnel>>>,
) -> Result<String> {
    storage::ensure_device_storage_ready(serial, 2222).await?;

    let identity_file = dirs::config_dir()
        .unwrap_or_else(|| PathBuf::from("/tmp"))
        .join("singularity/storage_id_ed25519");

    let tunnel = AdbTunnel::forward_tcp(serial, 2222).await?;
    let port = tunnel.local_port();

    let phone_mount = PathBuf::from("/mnt/phone");
    let root_mount = PathBuf::from("/mnt/root");

    // 1. Mount phone internal storage (/data/media/0) to /mnt/phone
    storage::mount_phone(port, "/data/media/0", &phone_mount, &identity_file)?;

    // 2. Mount root filesystem (/) to /mnt/root
    let mut mounted_list = vec![phone_mount.clone()];
    match storage::mount_phone(port, "/", &root_mount, &identity_file) {
        Ok(()) => {
            mounted_list.push(root_mount.clone());
        }
        Err(e) => {
            tracing::warn!(target: "storage_auto", error = %e, "Failed mounting / to /mnt/root");
        }
    }

    *storage_tunnel.lock().await = Some(tunnel);
    let mut guard = active_mounts.lock().await;
    *guard = mounted_list;

    tracing::info!(
        target: "storage_auto",
        "Phone storage transparently mounted at /mnt/phone (/sdcard) and /mnt/root (/)"
    );

    Ok("/mnt/phone, /mnt/root".to_string())
}

pub async fn auto_mount_phone_storage(
    serial: &str,
    has_root: bool,
    auto_mount_enabled: bool,
    active_mounts: Arc<Mutex<Vec<PathBuf>>>,
    storage_tunnel: Arc<Mutex<Option<AdbTunnel>>>,
) -> Result<bool> {
    if !auto_mount_enabled {
        tracing::debug!(target: "storage_auto", "Storage auto-mount disabled by configuration");
        return Ok(false);
    }

    if !has_root {
        tracing::info!(target: "storage_auto", "Device lacks root bridge; transparent storage auto-mount skipped");
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

    mount_dual_storage(serial, active_mounts, storage_tunnel).await?;
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
        assert!(storage_tunnel.lock().await.is_none());
    }

    #[tokio::test]
    async fn test_auto_mount_skipped_when_no_root() {
        let active_mounts = Arc::new(Mutex::new(Vec::new()));
        let storage_tunnel = Arc::new(Mutex::new(None));
        let res = auto_mount_phone_storage("127.0.0.1:5555", false, true, active_mounts.clone(), storage_tunnel.clone()).await;
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
        let active_mounts = Arc::new(Mutex::new(vec![PathBuf::from("/mnt/phone"), PathBuf::from("/mnt/root")]));
        let storage_tunnel = Arc::new(Mutex::new(None));
        let res = auto_mount_phone_storage("127.0.0.1:5555", true, true, active_mounts.clone(), storage_tunnel.clone()).await;
        assert_eq!(res.unwrap(), true);
    }

    #[tokio::test]
    async fn test_auto_mount_fails_gracefully_on_unreachable_serial() {
        let active_mounts = Arc::new(Mutex::new(Vec::new()));
        let storage_tunnel = Arc::new(Mutex::new(None));
        let res = auto_mount_phone_storage("127.0.0.1:59998", true, true, active_mounts.clone(), storage_tunnel.clone()).await;
        assert!(res.is_err());
        assert!(active_mounts.lock().await.is_empty());
        assert!(storage_tunnel.lock().await.is_none());
    }
}
