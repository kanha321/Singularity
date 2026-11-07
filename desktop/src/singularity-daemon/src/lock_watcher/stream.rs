use std::sync::atomic::{AtomicU64, Ordering};
use tokio::io::{AsyncBufReadExt, AsyncWriteExt, BufReader};
use crate::auth_cache::SharedAuthCache;
use crate::error::{DaemonError, Result};
use crate::transport::AdbTunnel;

static LAST_SERVICE_START_ATTEMPT: AtomicU64 = AtomicU64::new(0);

#[derive(Debug, Default, PartialEq, Eq)]
pub struct LockActions {
    pub unlock: bool, pub lock: bool, pub logout: bool,
    pub power: Option<String>, pub open_url: Option<String>,
    pub camera: Option<String>, pub camera_lens: Option<String>,
    pub media_action: Option<singularity_protocol::MediaAction>,
    pub phone_media: Option<singularity_protocol::MediaState>,
    pub audio_command: Option<singularity_protocol::AudioRouteCommand>,
}

fn get_local_ip_for(target: &str) -> Option<String> {
    let host = target.split(':').next()?;
    if host == "auto" || host.is_empty() { return None; }
    let socket = std::net::UdpSocket::bind("0.0.0.0:0").ok()?;
    socket.connect(format!("{}:80", host)).ok()?;
    socket.local_addr().ok().map(|a| a.ip().to_string())
}

pub struct AppBridgeStream {
    reader: BufReader<tokio::net::unix::OwnedReadHalf>,
    writer: tokio::net::unix::OwnedWriteHalf,
    adb_address: String,
}

impl AppBridgeStream {
    pub async fn connect(adb_address: &str) -> Result<Self> {
        let now = std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).unwrap_or_default().as_secs();
        if now.saturating_sub(LAST_SERVICE_START_ATTEMPT.load(Ordering::Relaxed)) >= 10 {
            LAST_SERVICE_START_ATTEMPT.store(now, Ordering::Relaxed);
            let _ = tokio::process::Command::new("adb")
                .args(["-s", adb_address, "shell", "am", "start-foreground-service", "-n", "com.kanhaji.singularity/.service.AppBridgeService"])
                .status().await;
        }

        let mut attempts = 0;
        let stream = loop {
            match AdbTunnel::connect_unix(adb_address, "singularity_app").await {
                Ok(s) => break s,
                Err(_e) if attempts < 2 => {
                    attempts += 1;
                    AdbTunnel::cleanup_unix(adb_address, "singularity_app").await;
                    tokio::time::sleep(std::time::Duration::from_millis(150)).await;
                }
                Err(e) => return Err(e),
            }
        };

        let (r, w) = stream.into_split();
        Ok(Self { reader: BufReader::new(r), writer: w, adb_address: adb_address.to_string() })
    }

    pub async fn exchange(
        &mut self,
        locked: bool,
        logged_in: bool,
        auth_cache: &SharedAuthCache,
        cam_status: &singularity_protocol::CameraStatus,
        media: Option<&singularity_protocol::MediaState>,
        phone_action: Option<&singularity_protocol::MediaAction>,
        audio_sinks: Option<&[singularity_protocol::AudioSinkInfo]>,
        active_audio_route: Option<&singularity_protocol::ActiveAudioRouteInfo>,
    ) -> Result<LockActions> {
        let lens_str = match cam_status.lens {
            singularity_protocol::CameraLens::Ultrawide => "ultrawide",
            singularity_protocol::CameraLens::Front => "front",
            singularity_protocol::CameraLens::Custom(_) => "custom",
            _ => "main",
        };
        let mut req = serde_json::json!({
            "action": "lock_state", "locked": locked, "logged_in": logged_in, "isLoggedIn": logged_in,
            "camera_active": cam_status.active, "camera_has_mic": cam_status.has_mic,
            "camera_lens": lens_str, "file_server_port": 8920, "media": media
        });
        if let Some(s) = audio_sinks { req["audio_sinks"] = serde_json::to_value(s).unwrap_or_default(); }
        if let Some(r) = active_audio_route { req["active_audio_route"] = serde_json::to_value(r).unwrap_or_default(); }
        if let Some(act) = phone_action { req["phone_media_action"] = serde_json::to_value(act).unwrap_or_default(); }
        if let Some(ip) = get_local_ip_for(&self.adb_address) { req["file_server_host"] = serde_json::json!(ip); }
        let mut req_str = serde_json::to_string(&req).map_err(|e| DaemonError::Protocol(e.into()))?;
        req_str.push('\n');
        self.writer.write_all(req_str.as_bytes()).await?;
        self.writer.flush().await?;

        let mut resp = String::new();
        if self.reader.read_line(&mut resp).await? == 0 {
            AdbTunnel::cleanup_unix(&self.adb_address, "singularity_app").await;
            return Err(std::io::Error::new(std::io::ErrorKind::UnexpectedEof, "Stream closed").into());
        }

        let val: serde_json::Value = serde_json::from_str(&resp).unwrap_or_default();
        if let Some(auth_val) = val.get("auth_settings") {
            if let Ok(settings) = serde_json::from_value(auth_val.clone()) { *auth_cache.write().await = settings; }
        }
        Ok(parse_lock_actions(&val))
    }
}

pub fn parse_lock_actions(v: &serde_json::Value) -> LockActions {
    LockActions {
        unlock: v.get("request_unlock").and_then(|u| u.as_bool()).unwrap_or(false),
        lock: v.get("request_lock").and_then(|u| u.as_bool()).unwrap_or(false),
        logout: v.get("request_logout").and_then(|u| u.as_bool()).unwrap_or(false),
        power: v.get("request_power").and_then(|p| p.as_str()).map(ToString::to_string),
        open_url: v.get("request_open_url").and_then(|u| u.as_str()).map(ToString::to_string),
        camera: v.get("request_camera").and_then(|c| c.as_str()).map(ToString::to_string),
        camera_lens: v.get("camera_lens").and_then(|l| l.as_str()).map(ToString::to_string),
        media_action: v.get("media_action").and_then(|m| serde_json::from_value(m.clone()).ok()),
        phone_media: v.get("phone_media").and_then(|m| serde_json::from_value(m.clone()).ok()),
        audio_command: v.get("audio_route").and_then(|a| serde_json::from_value(a.clone()).ok()),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_parse_lock_actions_with_phone_media_and_audio() {
        let json = serde_json::json!({
            "status": "ok", "request_unlock": true,
            "phone_media": {
                "player": "com.google.android.youtube", "title": "YT Video",
                "artist": "Channel", "album": "Playlist", "status": "Playing",
                "position_ms": 1000, "duration_ms": 5000,
                "can_play": true, "can_pause": true, "can_next": true, "can_prev": false, "can_seek": true
            },
            "audio_route": { "action": "set_route", "target_sink": "alsa_output.hdmi" }
        });
        let actions = parse_lock_actions(&json);
        assert!(actions.unlock);
        let phone = actions.phone_media.expect("phone_media present");
        assert_eq!(phone.player, "com.google.android.youtube");
        assert_eq!(actions.audio_command.unwrap().target_sink.as_deref(), Some("alsa_output.hdmi"));
    }
}
