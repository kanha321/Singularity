use std::path::PathBuf;
use crate::bridge::DeviceBridge;
use crate::error::{CoreError, Result};
use crate::features::storage::config::StorageConfig;

pub fn resolve_mount_point(config: &StorageConfig) -> PathBuf {
    config.mount_point.clone()
}

pub fn ensure_host_ssh_key() -> Result<(PathBuf, String)> {
    let config_dir = dirs::config_dir()
        .unwrap_or_else(|| PathBuf::from("/tmp"))
        .join("singularity");
    std::fs::create_dir_all(&config_dir)
        .map_err(|e| CoreError::BridgeFailure {
            bridge: "Storage",
            message: format!("Failed to create config directory: {}", e),
        })?;

    let priv_key = config_dir.join("storage_id_ed25519");
    let pub_key_path = config_dir.join("storage_id_ed25519.pub");

    if !priv_key.exists() || !pub_key_path.exists() {
        tracing::info!(target: "storage_handler", "Generating dedicated ED25519 keypair for SFTP storage");
        let status = std::process::Command::new("ssh-keygen")
            .args([
                "-t", "ed25519",
                "-N", "",
                "-f", priv_key.to_string_lossy().as_ref(),
                "-C", "singularity-storage",
            ])
            .status()
            .map_err(|e| CoreError::BridgeFailure {
                bridge: "Storage",
                message: format!("ssh-keygen execution failed: {}", e),
            })?;

        if !status.success() {
            return Err(CoreError::BridgeFailure {
                bridge: "Storage",
                message: "ssh-keygen returned non-zero exit code".into(),
            });
        }
    }

    let pub_key = std::fs::read_to_string(&pub_key_path)
        .map_err(|e| CoreError::BridgeFailure {
            bridge: "Storage",
            message: format!("Failed reading public key: {}", e),
        })?;

    Ok((priv_key, pub_key.trim().to_string()))
}

pub async fn authorize_key_on_device(pub_key: &str, device: &DeviceBridge) -> Result<()> {
    let root = device.root()?;
    let cmd = format!(
        "mkdir -p /data/ssh && touch /data/ssh/authorized_keys && grep -qF '{0}' /data/ssh/authorized_keys || echo '{0}' >> /data/ssh/authorized_keys && chmod 600 /data/ssh/authorized_keys",
        pub_key
    );
    root.exec_su(&cmd).await?;
    tracing::info!(target: "storage_handler", "Public key authorized in /data/ssh/authorized_keys on device");
    Ok(())
}

pub async fn start_device_sshd(port: u16, device: &DeviceBridge) -> Result<()> {
    let root = device.root()?;
    let keygen_cmd = "mkdir -p /data/ssh && chmod 700 /data/ssh && if [ ! -f /data/ssh/ssh_host_ed25519_key ]; then /product/bin/ssh-keygen -t ed25519 -N '' -f /data/ssh/ssh_host_ed25519_key && chmod 600 /data/ssh/ssh_host_ed25519_key; fi";
    root.exec_su(keygen_cmd).await?;

    let sshd_cmd = format!(
        "pkill -9 sshd 2>/dev/null; env LD_LIBRARY_PATH=/data/data/com.termux/files/usr/lib /data/data/com.termux/files/usr/bin/sshd -p {0} -h /data/ssh/ssh_host_ed25519_key -o 'ListenAddress 127.0.0.1' -o 'AuthorizedKeysFile /data/ssh/authorized_keys' -o 'StrictModes no' -o 'PasswordAuthentication no' -o 'Subsystem sftp /data/data/com.termux/files/usr/libexec/sftp-server'",
        port
    );
    root.exec_su(&sshd_cmd).await?;
    tracing::info!(target: "storage_handler", port, "Device root OpenSSH SFTP daemon active on loopback");
    Ok(())
}

pub async fn stop_device_sshd(device: &DeviceBridge) -> Result<()> {
    if let Ok(root) = device.root() {
        let _ = root.exec_su("pkill -9 sshd").await;
        tracing::info!(target: "storage_handler", "Device root OpenSSH SFTP daemon terminated");
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn resolve_mount_point_returns_configured_path() {
        let config = StorageConfig::default();
        let path = resolve_mount_point(&config);
        assert_eq!(path, PathBuf::from("/mnt/phone"));
    }

    #[test]
    fn ensure_host_ssh_key_creates_and_loads_key() {
        let result = ensure_host_ssh_key();
        assert!(result.is_ok(), "Key generation and loading should succeed: {:?}", result.err());
        let (priv_path, pub_str) = result.unwrap();
        assert!(priv_path.exists());
        assert!(pub_str.starts_with("ssh-ed25519"));
    }
}
