use tokio::io::{AsyncBufReadExt, AsyncWriteExt, BufReader};
use crate::auth_cache::SharedAuthCache;
use crate::error::{DaemonError, Result};
use crate::transport::AdbTunnel;

#[derive(Debug, Default, PartialEq, Eq)]
pub struct LockActions {
    pub unlock: bool,
    pub lock: bool,
    pub logout: bool,
    pub power: Option<String>,
}

pub struct AppBridgeStream {
    reader: BufReader<tokio::net::unix::OwnedReadHalf>,
    writer: tokio::net::unix::OwnedWriteHalf,
}

impl AppBridgeStream {
    pub async fn connect(adb_address: &str) -> Result<Self> {
        let _ = tokio::process::Command::new("adb")
            .args([
                "-s", adb_address,
                "shell", "am", "start-foreground-service",
                "-n", "com.kanhaji.singularity/.service.AppBridgeService",
            ])
            .status()
            .await;

        let mut attempts = 0;
        let stream = loop {
            match AdbTunnel::connect_unix(adb_address, "singularity_app").await {
                Ok(s) => break s,
                Err(_e) if attempts < 3 => {
                    attempts += 1;
                    tokio::time::sleep(std::time::Duration::from_millis(150)).await;
                }
                Err(e) => return Err(e),
            }
        };

        let (r, w) = stream.into_split();
        Ok(Self { reader: BufReader::new(r), writer: w })
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
    ) -> Result<LockActions> {
        let req = serde_json::json!({
            "action": "lock_state", "locked": locked, "logged_in": logged_in, "isLoggedIn": logged_in
        });
        let mut req_str = serde_json::to_string(&req).map_err(|e| DaemonError::Protocol(e.into()))?;
        req_str.push('\n');
        self.writer.write_all(req_str.as_bytes()).await?;
        self.writer.flush().await?;

        let mut resp = String::new();
        let bytes_read = self.reader.read_line(&mut resp).await?;
        if bytes_read == 0 {
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
        })
    }
}
