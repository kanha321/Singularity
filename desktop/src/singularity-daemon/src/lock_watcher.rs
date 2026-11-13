pub mod action_handler;
pub mod adapter;
pub mod adapters;
pub mod audio_action;
pub mod detector;
pub mod input_activity;
pub mod media_sync;
pub mod proximity;
pub mod session;
pub mod socket2;
pub mod stream;

pub use action_handler::handle_lock_actions;
#[allow(unused_imports)]
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
        let mut audio_worker = crate::audio::AudioWorker::new();
        let notif_hub = crate::notification::NotificationHub::global().await;

        let mut client: Option<AppBridgeStream> = None;
        let mut last_addr = String::new();
        let mut cached_sinks = Vec::new();
        let mut last_sink_refresh = std::time::Instant::now() - Duration::from_secs(10);
        let mut cached_route = None;
        let mut last_route_refresh = std::time::Instant::now() - Duration::from_secs(10);
        let mut last_phone_media = None;
        let mut last_comp_ping = std::time::Instant::now();

        loop {
            let adb_address = active_device.read().await.clone();
            if adb_address == "auto" {
                client = None;
                audio_worker.stop().await;
                proximity.on_disconnected(&*adapter).await;
                tokio::time::sleep(Duration::from_millis(500)).await;
                continue;
            }
            if adb_address != last_addr {
                last_addr = adb_address.clone();
                client = None;
                audio_worker.stop().await;
            }

            let cur_locked = query_pc_screen_locked(&*adapter).await;
            let cur_logged_in = query_pc_logged_in().await;
            if client.is_none() { client = AppBridgeStream::connect(&adb_address).await.ok(); }

            if last_sink_refresh.elapsed() >= Duration::from_millis(2000) || cached_sinks.is_empty() {
                cached_sinks = crate::audio::query_laptop_sinks().await;
                last_sink_refresh = std::time::Instant::now();
            }

            if last_route_refresh.elapsed() >= Duration::from_millis(500) || cached_route.is_none() {
                let w_sink = if audio_worker.is_active() { audio_worker.current_sink() } else { None };
                cached_route = audio_action::compute_active_audio_route(w_sink, &cached_sinks, last_phone_media.as_ref()).await;
                last_route_refresh = std::time::Instant::now();
            }

            let cam_status = camera.status().await;
            let media = crate::media::mpris::get_active_media_state(None, 8920).await;
            let phone_action = media_sync.pop_action();
            let mut fast_loop = phone_action.is_some();
            let mut phone_playing = false;

            let (d_notifs, d_dism, d_actions) = notif_hub.pop_events_for_phone().await;
            let ring_cmd = if client.is_some() { crate::find::pop_ring_request() } else { None };
            if let Some(ref mut stream) = client {
                match stream.exchange(
                    cur_locked, cur_logged_in, &auth_cache, &cam_status, media.as_ref(),
                    phone_action.as_ref(), Some(&cached_sinks), cached_route.as_ref(),
                    Some(&d_notifs), Some(&d_dism), Some(&d_actions),
                    ring_cmd,
                ).await {
                    Ok(actions) => {
                        if let Some(ringing) = actions.phone_ringing {
                            let was_ringing = crate::find::is_ringing();
                            crate::find::update_ringing_state(ringing);
                            if was_ringing && !ringing {
                                tokio::spawn(async {
                                    let _ = tokio::process::Command::new("notify-send")
                                        .args(["-u", "normal", "-i", "dialog-information", "-a", "Singularity", "✅ Device Located", "Phone siren was silenced from the phone."])
                                        .status().await;
                                });
                            }
                        }
                        for p_n in &actions.phone_notifications { notif_hub.post_phone_notification(p_n.clone(), cur_locked).await; }
                        for p_d in &actions.phone_dismissals { notif_hub.dismiss_from_phone(&p_d.id).await; }
                        proximity.on_connected(&*adapter).await;
                        fast_loop = fast_loop || actions.camera.is_some() || actions.media_action.is_some() || actions.audio_command.is_some();
                        phone_playing = actions.phone_media.as_ref().map(|m| m.status.eq_ignore_ascii_case("playing")).unwrap_or(false);
                        last_phone_media = actions.phone_media.clone();
                        media_sync.sync_states(media.as_ref(), actions.phone_media.as_ref()).await;
                        handle_lock_actions(&actions, &*adapter, &auth_cache, &camera, &mut audio_worker, &adb_address, &mut cached_sinks).await;
                        if last_comp_ping.elapsed() >= Duration::from_secs(5) {
                            last_comp_ping = std::time::Instant::now();
                            let comp_dev = adb_address.clone();
                            tokio::spawn(async move { crate::companion::ping(&comp_dev).await; });
                        }
                    }
                    Err(e) => {
                        if let Some(c) = ring_cmd { crate::find::set_ring_request(c); }
                        tracing::debug!(target: "lock_watcher", "AppBridge socket dropped: {}, will reconnect", e);
                        client = None;
                        let comp_dev = adb_address.clone();
                        tokio::spawn(async move { crate::companion::kill_audio(&comp_dev).await; });
                        audio_worker.stop().await;
                        crate::audio::worker::cleanup_remote_audio_server(&adb_address).await;
                        crate::transport::AdbTunnel::cleanup_unix(&adb_address, "singularity_app").await;
                        proximity.on_disconnected(&*adapter).await;
                    }
                }
            } else {
                audio_worker.stop().await;
                proximity.on_disconnected(&*adapter).await;
            }

            proximity.check_inactivity_lock(&*adapter, cur_locked).await;
            let is_media_playing = media.as_ref().map(|m| m.status.eq_ignore_ascii_case("playing")).unwrap_or(false) || phone_playing;
            let sleep_dur = if fast_loop { Duration::from_millis(50) } else if is_media_playing { Duration::from_millis(250) } else { Duration::from_millis(1000) };
            tokio::select! {
                _ = notify.notified() => {},
                _ = tokio::time::sleep(sleep_dur) => {},
            }
        }
    })
}
