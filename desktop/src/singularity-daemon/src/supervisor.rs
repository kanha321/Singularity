use std::path::PathBuf;
use std::sync::atomic::AtomicBool;
use std::sync::Arc;
use std::time::Duration;
use tokio::sync::{broadcast, Mutex, RwLock};

use crate::clipboard::ClipboardBridge;
use crate::storage;
use crate::transport::{resolve_active_device, AdbTunnel, ScrcpySession};
use singularity_core::bridge::{DeviceBridge, DeviceInfo, RootBridge, ShellBridge};
use singularity_core::FeatureRegistry;

#[allow(clippy::too_many_arguments)]
pub fn start_supervisor(
    configured_address: String,
    reconnect_secs: u64,
    simulate_non_root: bool,
    active_device: Arc<RwLock<String>>,
    registry: Arc<RwLock<FeatureRegistry>>,
    clip_tx: broadcast::Sender<String>,
    active_mount: Arc<Mutex<Option<PathBuf>>>,
    storage_tunnel: Arc<Mutex<Option<AdbTunnel>>>,
    has_root: Arc<AtomicBool>,
) -> tokio::task::JoinHandle<()> {
    tokio::spawn(async move {
        let bridge = ClipboardBridge::new();
        loop {
            let target_device = match resolve_active_device(&configured_address).await {
                Some(dev) => {
                    *active_device.write().await = dev.clone();
                    dev
                }
                None => {
                    let sleep_secs = if configured_address == "auto" { 2 } else { reconnect_secs };
                    tracing::warn!(
                        target: "daemon",
                        configured = %configured_address,
                        retry_in_sec = sleep_secs,
                        "No Android device detected, will retry"
                    );
                    tokio::time::sleep(Duration::from_secs(sleep_secs)).await;
                    continue;
                }
            };

            tracing::info!(target: "daemon", target_device = %target_device, "Attempting connection to Android device");
            match ScrcpySession::connect(&target_device).await {
                Ok(session) => {
                    tracing::info!(
                        target: "daemon",
                        device = %session.device_name,
                        "Phone highway online. Active bi-directional clipboard sync engaged"
                    );

                    let info = DeviceInfo {
                        serial: target_device.clone(),
                        model: session.device_name.clone(),
                        android_version: "16 (Evolution X)".into(),
                    };
                    let root_bridge = if simulate_non_root {
                        None
                    } else {
                        let runner = Arc::new(crate::transport::AdbRootRunner::new(&target_device));
                        Some(Arc::new(RootBridge::with_runner(runner)))
                    };
                    let shell_runner = Arc::new(crate::transport::AdbShellRunner::new(&target_device));
                    let shell_bridge = Arc::new(ShellBridge::with_runner(shell_runner));
                    let app_bridge = Some(Arc::new(singularity_core::bridge::AppBridge::new()));
                    let device = DeviceBridge::new(info, root_bridge, shell_bridge, app_bridge);

                    has_root.store(device.has_root(), std::sync::atomic::Ordering::SeqCst);
                    registry.write().await.on_connected(&device).await;

                    let local_rx = clip_tx.subscribe();
                    let (read_half, write_half) = session.stream.into_split();
                    if let Err(e) = bridge.run(local_rx, read_half, write_half).await {
                        tracing::warn!(target: "daemon", error = %e, "Clipboard bridge session terminated");
                    }

                    registry.write().await.on_disconnected().await;
                    has_root.store(false, std::sync::atomic::Ordering::SeqCst);
                    if configured_address == "auto" {
                        *active_device.write().await = "auto".to_string();
                    }
                    if let Some(mount) = active_mount.lock().await.take() {
                        let _ = storage::unmount_phone(&mount);
                    }
                    *storage_tunnel.lock().await = None;
                }
                Err(e) => {
                    tracing::warn!(
                        target: "daemon",
                        error = %e,
                        retry_in_sec = reconnect_secs,
                        "Failed connecting to target device, will retry"
                    );
                }
            }
            tokio::time::sleep(Duration::from_secs(reconnect_secs)).await;
        }
    })
}
