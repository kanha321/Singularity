pub mod action_handler;
pub mod adapter;
pub mod adapters;
pub mod detector;
pub mod input_activity;
pub mod media_sync;
pub mod proximity;
pub mod session;
pub mod socket2;
pub mod stream;

pub use action_handler::handle_lock_actions;
pub use adapter::LockerAdapter;
pub use detector::resolve_adapter;
pub use input_activity::InputActivityTracker;
pub use media_sync::MediaSynchronizer;
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
        let mut media_sync = MediaSynchronizer::start().await;

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

            let cam_status = camera.status().await;
            let media = crate::media::mpris::get_active_media_state(None, 8920).await;
            let phone_action = media_sync.pop_action();
            let mut fast_loop = phone_action.is_some();
            let mut phone_playing = false;

            if let Some(ref mut stream) = client {
                match stream.exchange(current_locked, current_logged_in, &auth_cache, &cam_status, media.as_ref(), phone_action.as_ref()).await {
                    Ok(actions) => {
                        proximity.on_connected(&*adapter).await;
                        fast_loop = fast_loop || actions.camera.is_some() || actions.media_action.is_some();
                        phone_playing = actions.phone_media.as_ref().map(|m| m.status.eq_ignore_ascii_case("playing")).unwrap_or(false);
                        media_sync.sync_states(media.as_ref(), actions.phone_media.as_ref()).await;
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

            let is_media_playing = media.as_ref().map(|m| m.status.eq_ignore_ascii_case("playing")).unwrap_or(false) || phone_playing;
            let sleep_dur = if fast_loop {
                Duration::from_millis(50)
            } else if is_media_playing {
                Duration::from_millis(250)
            } else {
                Duration::from_millis(1000)
            };
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
