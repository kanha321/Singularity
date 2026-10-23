use std::path::{Path, PathBuf};
use tokio::process::Command;
use crate::error::{DaemonError, Result};

#[derive(Debug, Clone, PartialEq, Eq)]
#[allow(dead_code)]
pub enum TunnelTarget {
    Tcp(u16),
    Unix(PathBuf),
}

/// Manages an active ADB forwarding tunnel (TCP loopback or UNIX domain socket).
#[derive(Debug)]
pub struct AdbTunnel {
    serial: String,
    target: TunnelTarget,
}

impl AdbTunnel {
    /// Connects to a local UNIX domain socket forward, establishing the forward rule if not already active.
    pub async fn connect_unix(serial: &str, abstract_socket: &str) -> Result<tokio::net::UnixStream> {
        let uid = unsafe { libc::getuid() };
        let name = abstract_socket.strip_prefix("singularity_").unwrap_or(abstract_socket);
        let socket_path = PathBuf::from(format!("/run/user/{}/singularity_{}.sock", uid, name));

        if let Ok(stream) = tokio::net::UnixStream::connect(&socket_path).await {
            return Ok(stream);
        }

        let _ = std::fs::remove_file(&socket_path);
        let output = Command::new("adb")
            .args(["-s", serial, "forward", &format!("localfilesystem:{}", socket_path.display()), &format!("localabstract:{}", abstract_socket)])
            .output().await?;

        if !output.status.success() {
            let stderr = String::from_utf8_lossy(&output.stderr);
            return Err(DaemonError::AdbTunnel(format!("Failed forward: {}", stderr.trim())));
        }
        tracing::info!(target: "adb_tunnel", serial = %serial, socket = %socket_path.display(), "ADB UNIX forward established");
        tokio::net::UnixStream::connect(&socket_path).await
            .map_err(|e| DaemonError::AdbTunnel(format!("Failed connecting to AppBridge socket: {}", e)))
    }

    /// Establishes dynamic port forwarding over ADB to a target device abstract socket (tcp:0 -> localabstract:NAME).
    pub async fn open(serial: &str, abstract_socket: &str) -> Result<Self> {
        let output = Command::new("adb")
            .args(["-s", serial, "forward", "tcp:0", &format!("localabstract:{}", abstract_socket)])
            .output().await?;

        if !output.status.success() {
            let stderr = String::from_utf8_lossy(&output.stderr);
            return Err(DaemonError::AdbTunnel(format!("Failed forward: {}", stderr.trim())));
        }
        let port = parse_forward_port(&String::from_utf8_lossy(&output.stdout))?;
        tracing::info!(target: "adb_tunnel", serial = %serial, port = port, abstract_socket = %abstract_socket, "ADB port forward established");
        Ok(Self { serial: serial.to_string(), target: TunnelTarget::Tcp(port) })
    }

    /// Establishes dynamic port forwarding over ADB to a target TCP port on the device (tcp:0 -> tcp:TARGET_PORT).
    pub async fn forward_tcp(serial: &str, target_port: u16) -> Result<Self> {
        let output = Command::new("adb")
            .args(["-s", serial, "forward", "tcp:0", &format!("tcp:{}", target_port)])
            .output().await?;

        if !output.status.success() {
            let stderr = String::from_utf8_lossy(&output.stderr);
            return Err(DaemonError::AdbTunnel(format!("Failed forward: {}", stderr.trim())));
        }
        let port = parse_forward_port(&String::from_utf8_lossy(&output.stdout))?;
        tracing::info!(target: "adb_tunnel", serial = %serial, port = port, target_port, "ADB TCP forward established");
        Ok(Self { serial: serial.to_string(), target: TunnelTarget::Tcp(port) })
    }

    pub fn local_port(&self) -> u16 {
        match &self.target {
            TunnelTarget::Tcp(port) => *port,
            TunnelTarget::Unix(_) => 0,
        }
    }

    #[allow(dead_code)]
    pub fn socket_path(&self) -> Option<&Path> {
        match &self.target {
            TunnelTarget::Unix(path) => Some(path.as_path()),
            TunnelTarget::Tcp(_) => None,
        }
    }

    #[allow(dead_code)]
    pub fn serial(&self) -> &str {
        &self.serial
    }
}

impl Drop for AdbTunnel {
    fn drop(&mut self) {
        let target_str = match &self.target {
            TunnelTarget::Tcp(port) if *port != 0 => format!("tcp:{}", port),
            TunnelTarget::Unix(path) => {
                let _ = std::fs::remove_file(path);
                format!("localfilesystem:{}", path.display())
            }
            _ => return,
        };
        let _ = std::process::Command::new("adb")
            .args(["-s", &self.serial, "forward", "--remove", &target_str])
            .status();
    }
}

/// Parses the numeric port from the output of `adb forward tcp:0 ...`
pub fn parse_forward_port(output: &str) -> Result<u16> {
    output
        .trim()
        .lines()
        .next()
        .ok_or_else(|| DaemonError::AdbTunnel("Empty ADB forward output".into()))?
        .parse::<u16>()
        .map_err(|e| DaemonError::AdbTunnel(format!("Invalid port '{}': {}", output.trim(), e)))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parse_forward_port_extracts_clean_integer() {
        let raw = "42007\n";
        assert_eq!(parse_forward_port(raw).unwrap(), 42007);
    }

    #[test]
    fn parse_forward_port_rejects_error_strings() {
        let raw = "error: device '10.235.203.184:5555' not found\n";
        assert!(parse_forward_port(raw).is_err());
    }

    #[test]
    fn tunnel_target_accessors_match_type() {
        let tcp = AdbTunnel { serial: "test".into(), target: TunnelTarget::Tcp(42007) };
        assert_eq!(tcp.local_port(), 42007);
        assert_eq!(tcp.socket_path(), None);

        let unix = AdbTunnel { serial: "test".into(), target: TunnelTarget::Unix(PathBuf::from("/tmp/singularity.sock")) };
        assert_eq!(unix.local_port(), 0);
        assert_eq!(unix.socket_path(), Some(Path::new("/tmp/singularity.sock")));
    }
}
