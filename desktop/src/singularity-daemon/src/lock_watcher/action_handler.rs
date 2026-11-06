use crate::auth_cache::SharedAuthCache;
use crate::camera::CameraSupervisor;
use crate::lock_watcher::adapter::LockerAdapter;
use crate::lock_watcher::session::*;
use crate::lock_watcher::stream::LockActions;
use singularity_protocol::CameraLens;

pub async fn handle_lock_actions(
    actions: &LockActions,
    adapter: &dyn LockerAdapter,
    auth_cache: &SharedAuthCache,
    camera: &CameraSupervisor,
    serial: &str,
) {
    if let Some(url) = &actions.open_url {
        tracing::info!(target: "lock_watcher", %url, "Opening URL on desktop requested by device");
        let url_clone = url.clone();
        tokio::spawn(async move {
            let _ = tokio::process::Command::new("xdg-open").arg(&url_clone).status().await;
        });
    }
    if actions.lock {
        trigger_pc_lock(adapter).await;
    }
    if actions.logout {
        trigger_pc_logout().await;
    }
    if let Some(ref power) = actions.power {
        handle_power_action(power.as_str(), adapter).await;
    }
    if actions.unlock {
        handle_unlock_action(adapter, auth_cache).await;
    }
    if let Some(ref cam_req) = actions.camera {
        handle_camera_action(cam_req.as_str(), actions.camera_lens.as_deref(), camera, serial).await;
    }
    if let Some(ref media_action) = actions.media_action {
        crate::media::mpris::execute_media_action(media_action).await;
    }
}

async fn handle_power_action(power: &str, adapter: &dyn LockerAdapter) {
    match power {
        "reboot" => trigger_pc_reboot().await,
        "shutdown" => trigger_pc_shutdown().await,
        "logout" => trigger_pc_logout().await,
        "suspend" => trigger_pc_suspend(adapter).await,
        "hibernate" => trigger_pc_hibernate(adapter).await,
        other => tracing::warn!(target: "lock_watcher", "Unknown power action: {}", other),
    }
}

async fn handle_unlock_action(adapter: &dyn LockerAdapter, auth_cache: &SharedAuthCache) {
    let cache = auth_cache.read().await;
    let denied = cache.is_denied("unlock") || cache.is_snoozed();
    drop(cache);
    if denied {
        tracing::warn!(target: "lock_watcher", "Ignored unlock request: unlock is denied or snoozed");
    } else {
        trigger_pc_unlock(adapter).await;
    }
}

async fn handle_camera_action(
    req: &str,
    lens_str: Option<&str>,
    camera: &CameraSupervisor,
    serial: &str,
) {
    match req {
        "start" => {
            let lens = lens_str.and_then(parse_lens).unwrap_or(CameraLens::Main);
            let mut cfg = singularity_protocol::CameraConfig::default();
            cfg.lens = lens;
            tracing::info!(target: "lock_watcher", ?lens, "Starting camera from phone request");
            if let Err(e) = camera.start(serial, Some(cfg)).await {
                tracing::error!(target: "lock_watcher", error = %e, "Failed to start camera from phone request");
            }
        }
        "stop" => {
            tracing::info!(target: "lock_watcher", "Stopping camera from phone request");
            if let Err(e) = camera.stop().await {
                tracing::error!(target: "lock_watcher", error = %e, "Failed to stop camera from phone request");
            }
        }
        "switch_lens" => {
            if let Some(lens) = lens_str.and_then(parse_lens) {
                tracing::info!(target: "lock_watcher", ?lens, "Switching camera lens from phone request");
                if let Err(e) = camera.switch_lens(serial, lens).await {
                    tracing::error!(target: "lock_watcher", error = %e, "Failed to switch camera lens");
                }
            }
        }
        other => tracing::warn!(target: "lock_watcher", "Unknown camera request: {}", other),
    }
}

fn parse_lens(s: &str) -> Option<CameraLens> {
    match s {
        "main" => Some(CameraLens::Main),
        "ultrawide" => Some(CameraLens::Ultrawide),
        "front" => Some(CameraLens::Front),
        _ => None,
    }
}

