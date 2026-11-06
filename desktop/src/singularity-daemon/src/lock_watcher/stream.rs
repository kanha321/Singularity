use std::sync::atomic::{AtomicU64, Ordering};
use tokio::io::{AsyncBufReadExt, AsyncWriteExt, BufReader};
use crate::auth_cache::SharedAuthCache;
use crate::error::{DaemonError, Result};
use crate::transport::AdbTunnel;

static LAST_SERVICE_START_ATTEMPT: AtomicU64 = AtomicU64::new(0);

#[derive(Debug, Default, PartialEq, Eq)]
pub struct LockActions {
    pub unlock: bool,
    pub lock: bool,
    pub logout: bool,
    pub power: Option<String>,
    pub open_url: Option<String>,
    pub camera: Option<String>,
    pub camera_lens: Option<String>,
    pub media_action: Option<singularity_protocol::MediaAction>,
}

fn get_local_ip_for(target: &str) -> Option<String> {
    let host = target.split(':').next()?;
    if host == "auto" || host.is_empty() {
        return None;
    }
    let socket = std::net::UdpSocket::bind("0.0.0.0:0").ok()?;
    socket.connect(format!("{}:80", host)).ok()?;
    Some(socket.local_addr().ok()?.ip().to_string())
}

pub struct AppBridgeStream {
    reader: BufReader<tokio::net::unix::OwnedReadHalf>,
    writer: tokio::net::unix::OwnedWriteHalf,
    adb_address: String,
}

impl AppBridgeStream {
    pub async fn connect(adb_address: &str) -> Result<Self> {
        let now = std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).unwrap_or_default().as_secs();
        let last = LAST_SERVICE_START_ATTEMPT.load(Ordering::Relaxed);
        if now.saturating_sub(last) >= 10 {
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

    #[allow(dead_code)]
    pub async fn shutdown(&mut self) -> Result<()> {
        let req = serde_json::json!({ "action": "shutdown" });
        let mut req_str = serde_json::to_string(&req).map_err(|e| DaemonError::Protocol(e.into()))?;
        req_str.push('\n');
        self.writer.write_all(req_str.as_bytes()).await?;
        self.writer.flush().await?;
        Ok(())
    }

    pub async fn exchange(
        &mut self,
        locked: bool,
        logged_in: bool,
        auth_cache: &SharedAuthCache,
        camera_active: bool,
        media: Option<&singularity_protocol::MediaState>,
    ) -> Result<LockActions> {
        let mut req = serde_json::json!({
            "action": "lock_state", "locked": locked, "logged_in": logged_in, "isLoggedIn": logged_in,
            "camera_active": camera_active,
            "file_server_port": 8920,
            "media": media
        });
        if let Some(ip) = get_local_ip_for(&self.adb_address) {
            req["file_server_host"] = serde_json::json!(ip);
        }
        let mut req_str = serde_json::to_string(&req).map_err(|e| DaemonError::Protocol(e.into()))?;
        req_str.push('\n');
        self.writer.write_all(req_str.as_bytes()).await?;
        self.writer.flush().await?;

        let mut resp = String::new();
        let bytes_read = self.reader.read_line(&mut resp).await?;
        if bytes_read == 0 {
            AdbTunnel::cleanup_unix(&self.adb_address, "singularity_app").await;
            return Err(std::io::Error::new(std::io::ErrorKind::UnexpectedEof, "Stream closed").into());
        }

        let val: serde_json::Value = serde_json::from_str(&resp).unwrap_or_default();
        if let Some(auth_val) = val.get("auth_settings") {
            if let Ok(settings) = serde_json::from_value(auth_val.clone()) {
                *auth_cache.write().await = settings;
            }
        }

        Ok(LockActions {
            unlock: val.get("request_unlock").and_then(|u| u.as_bool()).unwrap_or(false),
            lock: val.get("request_lock").and_then(|u| u.as_bool()).unwrap_or(false),
            logout: val.get("request_logout").and_then(|u| u.as_bool()).unwrap_or(false),
            power: val.get("request_power").and_then(|p| p.as_str()).map(|s| s.to_string()),
            open_url: val.get("request_open_url").and_then(|u| u.as_str()).map(|s| s.to_string()),
            camera: val.get("request_camera").and_then(|c| c.as_str()).map(|s| s.to_string()),
            camera_lens: val.get("camera_lens").and_then(|l| l.as_str()).map(|s| s.to_string()),
            media_action: val.get("media_action").and_then(|m| serde_json::from_value(m.clone()).ok()),
        })
    }
}
