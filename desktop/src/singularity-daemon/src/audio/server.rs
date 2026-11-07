use std::process::Stdio;
use std::sync::Arc;
use tokio::net::TcpListener;
use tokio::process::Command;
use tokio::sync::RwLock;
use crate::error::{DaemonError, Result};

pub const PHONE_SINK_NAME: &str = "singularity_phone_sink";
pub const DEFAULT_AUDIO_SERVER_PORT: u16 = 8921;

pub async fn ensure_phone_sink() -> Result<()> {
    let out = Command::new("pactl").args(["-f", "json", "list", "sinks"]).output().await.map_err(DaemonError::Io)?;
    let json_str = String::from_utf8_lossy(&out.stdout);
    if json_str.contains(PHONE_SINK_NAME) {
        return Ok(());
    }
    let status = Command::new("pactl")
        .args([
            "load-module",
            "module-null-sink",
            &format!("sink_name={PHONE_SINK_NAME}"),
            "sink_properties=device.description=Singularity_Phone_Speaker",
        ])
        .status()
        .await
        .map_err(DaemonError::Io)?;
    if !status.success() {
        return Err(DaemonError::Storage("Failed to create singularity_phone_sink".into()));
    }
    Ok(())
}

async fn handle_audio_client(mut socket: tokio::net::TcpStream) {
    let _ = ensure_phone_sink().await;
    let monitor = format!("{PHONE_SINK_NAME}.monitor");
    let mut child = match Command::new("parec")
        .args(["--device", &monitor, "--raw", "--rate=48000", "--channels=2", "--format=s16le", "--latency-msec=35"])
        .stdout(Stdio::piped())
        .stderr(Stdio::null())
        .spawn()
    {
        Ok(c) => c,
        Err(e) => {
            tracing::error!(target: "audio_server", error = %e, "Failed to spawn parec");
            return;
        }
    };

    if let Some(mut stdout) = child.stdout.take() {
        let (_, mut socket_tx) = socket.split();
        let _ = tokio::io::copy(&mut stdout, &mut socket_tx).await;
    }
    let _ = child.kill().await;
    let _ = child.wait().await;
}

pub fn start_audio_server(active_device: Arc<RwLock<String>>, port: u16) -> tokio::task::JoinHandle<()> {
    tokio::spawn(async move {
        let listener = match TcpListener::bind(("127.0.0.1", port)).await {
            Ok(l) => l,
            Err(e) => {
                tracing::error!(target: "audio_server", error = %e, port, "Failed to bind audio TCP server");
                return;
            }
        };
        tracing::info!(target: "audio_server", port, "Audio PCM stream server online");

        let adb_dev = Arc::clone(&active_device);
        tokio::spawn(async move {
            loop {
                let current_dev = adb_dev.read().await.clone();
                if current_dev != "auto" {
                    let _ = Command::new("adb")
                        .args(["-s", &current_dev, "reverse", &format!("tcp:{port}"), &format!("tcp:{port}")])
                        .status()
                        .await;
                }
                tokio::time::sleep(std::time::Duration::from_secs(5)).await;
            }
        });

        loop {
            match listener.accept().await {
                Ok((socket, addr)) => {
                    tracing::info!(target: "audio_server", client = %addr, "Audio streaming client connected");
                    tokio::spawn(handle_audio_client(socket));
                }
                Err(e) => {
                    tracing::warn!(target: "audio_server", error = %e, "Failed to accept audio client");
                }
            }
        }
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_phone_sink_constants() {
        assert_eq!(PHONE_SINK_NAME, "singularity_phone_sink");
        assert_eq!(DEFAULT_AUDIO_SERVER_PORT, 8921);
    }
}
