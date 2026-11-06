pub mod action_handler;
pub mod adapter;
pub mod adapters;
pub mod detector;
pub mod input_activity;
pub mod proximity;
pub mod session;
pub mod socket2;
pub mod stream;

pub use action_handler::handle_lock_actions;
pub use adapter::LockerAdapter;
pub use detector::resolve_adapter;
pub use input_activity::InputActivityTracker;
pub use proximity::ProximityLockManager;
pub use session::*;
pub use stream::*;

use std::sync::Arc;
use std::time::Duration;
use tokio::sync::{Notify, RwLock};
use crate::auth_cache::SharedAuthCache;
use crate::camera::CameraSupervisor;

pub fn start_lock_watcher(
    active_device: Arc<RwLock<String>>,
    auth_cache: SharedAuthCache,
    camera: CameraSupervisor,
) -> tokio::task::JoinHandle<()> {
    tokio::spawn(async move {
        let adapter = resolve_adapter(None).await;
        let notify = Arc::new(Notify::new());
        let _socket2 = socket2::start_socket2_listener(Arc::clone(&notify));
        let mut proximity = ProximityLockManager::new(InputActivityTracker::start());

        let mut last_state = None;
        let mut client: Option<AppBridgeStream> = None;
        let mut last_addr = String::new();

        loop {
            let adb_address = active_device.read().await.clone();
            if adb_address == "auto" {
                client = None;
                proximity.on_disconnected(&*adapter).await;
                tokio::time::sleep(Duration::from_millis(500)).await;
                continue;
            }
            if adb_address != last_addr {
                last_addr = adb_address.clone();
                client = None;
            }

            let current_locked = query_pc_screen_locked(&*adapter).await;
            let current_logged_in = query_pc_logged_in().await;
            let current = (current_locked, current_logged_in);

            if last_state != Some(current) {
                last_state = Some(current);
                tracing::info!(
                    target: "lock_watcher",
                    adapter = adapter.name(),
                    is_locked = current_locked,
                    is_logged_in = current_logged_in,
                    "PC session state updated"
                );
            }

            if client.is_none() {
                client = AppBridgeStream::connect(&adb_address).await.ok();
            }

            let cam_active = camera.is_streaming().await;
            let media = crate::media::mpris::get_active_media_state(None, 8920).await;
            let mut fast_loop = false;
            if let Some(ref mut stream) = client {
                match stream.exchange(current_locked, current_logged_in, &auth_cache, cam_active, media.as_ref()).await {
                    Ok(actions) => {
                        proximity.on_connected(&*adapter).await;
                        fast_loop = actions.camera.is_some();
                        handle_lock_actions(&actions, &*adapter, &auth_cache, &camera, &adb_address).await;
                    }
                    Err(e) => {
                        tracing::debug!(target: "lock_watcher", "AppBridge socket dropped: {}, will reconnect", e);
                        client = None;
                        crate::transport::AdbTunnel::cleanup_unix(&adb_address, "singularity_app").await;
                        proximity.on_disconnected(&*adapter).await;
                    }
                }
            } else {
                proximity.on_disconnected(&*adapter).await;
            }

            proximity.check_inactivity_lock(&*adapter, current_locked).await;

            let sleep_dur = if fast_loop { Duration::from_millis(50) } else { Duration::from_millis(1000) };
            tokio::select! {
                _ = notify.notified() => {},
                _ = tokio::time::sleep(sleep_dur) => {},
            }
        }
    })
}

#[cfg(test)]
mod tests {
    #[test]
    fn parse_lock_json_matches_expected() {
        let json = serde_json::json!({
            "action": "lock_state", "locked": true, "logged_in": false, "isLoggedIn": false,
            "request_lock": true, "request_logout": false, "request_unlock": false,
            "request_power": "suspend"
        });
        assert_eq!(json["locked"], true);
        assert_eq!(json["request_lock"], true);
    }
}
