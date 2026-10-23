use std::time::Duration;
use tokio::io::AsyncReadExt;
use tokio::net::TcpStream;
use tokio::process::{Child, Command};
use crate::assets::SCRCPY_SERVER_REMOTE_PATH;
use crate::deployer::ServerDeployer;
use crate::error::{DaemonError, Result};
use super::adb_tunnel::AdbTunnel;

pub struct ScrcpySession {
    pub _tunnel: AdbTunnel,
    pub _process: Child,
    pub stream: TcpStream,
    pub device_name: String,
}

impl ScrcpySession {
    /// Ensures scrcpy-server.jar is deployed on device, launches server, and establishes TCP connection.
    pub async fn connect(serial: &str) -> Result<Self> {
        Self::ensure_server_deployed(serial).await?;

        let tunnel = AdbTunnel::open(serial, "scrcpy").await?;
        let shell_cmd = ServerDeployer::build_shell_command(true);

        tracing::info!(target: "scrcpy_session", serial = %serial, "Launching scrcpy-server process");
        let process = Command::new("adb")
            .args(["-s", serial, "shell", &shell_cmd])
            .kill_on_drop(true)
            .spawn()?;

        let (stream, device_name) = Self::connect_and_handshake_with_retry(tunnel.local_port()).await?;

        tracing::info!(target: "scrcpy_session", device_name = %device_name, "scrcpy-server session established");
        Ok(Self {
            _tunnel: tunnel,
            _process: process,
            stream,
            device_name,
        })
    }

    async fn ensure_server_deployed(serial: &str) -> Result<()> {
        let check = Command::new("adb")
            .args(["-s", serial, "shell", &format!("ls {}", SCRCPY_SERVER_REMOTE_PATH)])
            .output()
            .await?;

        if !check.status.success() {
            tracing::info!(target: "scrcpy_session", serial = %serial, "Server bytecode missing on device; deploying embedded asset");
            let temp_file = ServerDeployer::create_staging_file()?;
            let push_status = Command::new("adb")
                .args(["-s", serial, "push", temp_file.path().to_str().unwrap(), SCRCPY_SERVER_REMOTE_PATH])
                .status()
                .await?;

            if !push_status.success() {
                return Err(DaemonError::AdbTunnel("Failed to push scrcpy-server.jar to device".into()));
            }
        }
        Ok(())
    }

    async fn connect_and_handshake_with_retry(port: u16) -> Result<(TcpStream, String)> {
        let addr = format!("127.0.0.1:{}", port);
        for attempt in 1..=25 {
            tokio::time::sleep(Duration::from_millis(200)).await;
            if let Ok(mut stream) = TcpStream::connect(&addr).await {
                let mut dummy = [0u8; 1];
                if stream.read_exact(&mut dummy).await.is_ok() && dummy[0] == 0 {
                    let mut banner = [0u8; 64];
                    if stream.read_exact(&mut banner).await.is_ok() {
                        let device_name = parse_device_banner(&banner);
                        return Ok((stream, device_name));
                    }
                }
            }
            tracing::trace!(target: "scrcpy_session", attempt = attempt, "Retrying scrcpy connection and handshake on {}", addr);
        }
        Err(DaemonError::AdbTunnel(format!("Timeout connecting to scrcpy forward port {}", port)))
    }
}

/// Parses the 64-byte null-padded device name banner emitted by scrcpy-server.
pub fn parse_device_banner(banner: &[u8; 64]) -> String {
    let nul_pos = banner.iter().position(|&b| b == 0).unwrap_or(banner.len());
    String::from_utf8_lossy(&banner[..nul_pos]).trim().to_string()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parse_device_banner_trims_null_bytes() {
        let mut banner = [0u8; 64];
        banner[..9].copy_from_slice(b"23049PCD8");
        assert_eq!(parse_device_banner(&banner), "23049PCD8");
    }

    #[test]
    fn parse_device_banner_handles_full_buffer_without_null() {
        let banner = [b'A'; 64];
        assert_eq!(parse_device_banner(&banner), "A".repeat(64));
    }
}
