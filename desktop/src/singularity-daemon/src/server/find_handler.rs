use std::sync::Arc;
use tokio::sync::RwLock;
use singularity_protocol::IpcResponse;
use crate::error::Result;

pub async fn handle_find_device(
    ring: bool,
    active_device: &Arc<RwLock<String>>,
) -> Result<IpcResponse> {
    crate::find::set_ring_request(ring);

    let dev_addr = active_device.read().await.clone();
    if !dev_addr.is_empty() && dev_addr != "auto" {
        tokio::spawn(async move {
            crate::find::send_direct_tcp_ring(&dev_addr, ring).await;
        });
    }

    if ring {
        tracing::info!(target: "find_handler", "Find My Device initiated: ringing phone at 100% volume");
        tokio::spawn(async {
            let _ = tokio::process::Command::new("notify-send")
                .args([
                    "-u", "critical",
                    "-i", "dialog-warning",
                    "-a", "Singularity",
                    "🔔 Finding Android Device",
                    "Phone siren active at 100% volume overriding DND.\nRun 'singularity find --stop' when found."
                ])
                .status()
                .await;
        });
    } else {
        tracing::info!(target: "find_handler", "Find My Device cancelled: silenced phone");
        tokio::spawn(async {
            let _ = tokio::process::Command::new("notify-send")
                .args([
                    "-u", "normal",
                    "-i", "dialog-information",
                    "-a", "Singularity",
                    "🔕 Phone Siren Stopped",
                    "Original audio volume and ringer mode restored."
                ])
                .status()
                .await;
        });
    }

    Ok(IpcResponse::FindDeviceResult { ringing: ring })
}
