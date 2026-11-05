use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use tokio::sync::RwLock;
use singularity_protocol::IpcResponse;

pub async fn handle_remount(
    active_device: &Arc<RwLock<String>>,
    has_root: &Arc<AtomicBool>,
    read_write: bool,
) -> IpcResponse {
    let is_root = has_root.load(Ordering::SeqCst);
    if !is_root {
        return IpcResponse::Error(
            "Device is not rooted (KernelSU/su required to remount system partitions)".into(),
        );
    }

    let adb_address = match crate::transport::resolve_or_wait_device(active_device, 3).await {
        Some(addr) => addr,
        None => return IpcResponse::Error("No Android device detected on network".into()),
    };

    let mode = if read_write { "rw" } else { "ro" };
    let cmd = format!(
        "mount -o remount,{0} / && mount -o remount,{0} /product 2>/dev/null; mount -o remount,{0} /vendor 2>/dev/null",
        mode
    );

    let res = tokio::process::Command::new("adb")
        .args(["-s", &adb_address, "shell", &format!("su -c '{}'", cmd)])
        .status()
        .await;

    match res {
        Ok(s) if s.success() => {
            let desc = if read_write {
                "read-write (rw)"
            } else {
                "read-only (ro)"
            };
            tracing::info!(target: "ipc_server", mode, "System partitions remounted successfully");
            IpcResponse::Custom(serde_json::json!(desc))
        }
        Ok(s) => IpcResponse::Error(format!("Remount failed with exit code: {:?}", s.code())),
        Err(e) => IpcResponse::Error(format!("Failed to execute remount command: {}", e)),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn test_remount_rejects_unrooted_device() {
        let active = Arc::new(RwLock::new("test".into()));
        let root = Arc::new(AtomicBool::new(false));
        let resp = handle_remount(&active, &root, true).await;
        match resp {
            IpcResponse::Error(e) => assert!(e.contains("not rooted")),
            _ => panic!("Expected error for unrooted device"),
        }
    }
}

