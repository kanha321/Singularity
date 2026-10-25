use tokio::process::Command;
use singularity_core::features::storage::handler::ensure_host_ssh_key;
use crate::error::{DaemonError, Result};

pub async fn ensure_device_storage_ready(serial: &str, port: u16) -> Result<()> {
    let (_, pub_key) = ensure_host_ssh_key().map_err(|e| DaemonError::Storage(e.to_string()))?;

    // 1. Verify Termux OpenSSH is installed
    let termux_check = Command::new("adb")
        .args(["-s", serial, "shell", "su -mm -c '[ -x /data/data/com.termux/files/usr/bin/sshd ]'"])
        .status()
        .await
        .map_err(|e| DaemonError::Storage(format!("Failed to query device for Termux sshd: {}", e)))?;

    if !termux_check.success() {
        return Err(DaemonError::Storage(
            "Termux OpenSSH not found on phone at /data/data/com.termux/files/usr/bin/sshd. Please install Termux and run 'pkg install openssh'".into(),
        ));
    }

    // 2. Authorize host public key on device
    let auth_cmd = format!(
        "su -mm -c 'mkdir -p /data/ssh && chmod 700 /data/ssh && touch /data/ssh/authorized_keys && grep -qF \"{0}\" /data/ssh/authorized_keys || echo \"{0}\" >> /data/ssh/authorized_keys && chmod 600 /data/ssh/authorized_keys'",
        pub_key
    );
    let auth_status = Command::new("adb")
        .args(["-s", serial, "shell", &auth_cmd])
        .status()
        .await
        .map_err(|e| DaemonError::Storage(format!("Failed to authorize key on device: {}", e)))?;

    if !auth_status.success() {
        return Err(DaemonError::Storage("Failed to authorize SSH key in /data/ssh/authorized_keys".into()));
    }

    // 3. Ensure device host key exists
    let keygen_cmd = "su -mm -c 'if [ ! -f /data/ssh/ssh_host_ed25519_key ]; then if [ -x /product/bin/ssh-keygen ]; then /product/bin/ssh-keygen -t ed25519 -N \"\" -f /data/ssh/ssh_host_ed25519_key; elif [ -x /data/data/com.termux/files/usr/bin/ssh-keygen ]; then env LD_LIBRARY_PATH=/data/data/com.termux/files/usr/lib /data/data/com.termux/files/usr/bin/ssh-keygen -t ed25519 -N \"\" -f /data/ssh/ssh_host_ed25519_key; fi && chmod 600 /data/ssh/ssh_host_ed25519_key; fi'";
    let _ = Command::new("adb")
        .args(["-s", serial, "shell", keygen_cmd])
        .status()
        .await;

    // 4. Ensure sshd is running
    let check_pid = Command::new("adb")
        .args(["-s", serial, "shell", "su -mm -c 'pidof sshd'"])
        .output()
        .await
        .map_err(|e| DaemonError::Storage(format!("Failed to check sshd process: {}", e)))?;

    let is_running = check_pid.status.success() && !check_pid.stdout.is_empty();
    if !is_running {
        tracing::info!(target: "storage_device", port, "Launching Termux OpenSSH daemon on phone");
        let start_cmd = format!(
            "su -mm -c 'env LD_LIBRARY_PATH=/data/data/com.termux/files/usr/lib /data/data/com.termux/files/usr/bin/sshd -p {0} -h /data/ssh/ssh_host_ed25519_key -o \"ListenAddress 127.0.0.1\" -o \"AuthorizedKeysFile /data/ssh/authorized_keys\" -o \"StrictModes no\" -o \"PasswordAuthentication no\" -o \"Subsystem sftp /data/data/com.termux/files/usr/libexec/sftp-server\"'",
            port
        );
        let start_status = Command::new("adb")
            .args(["-s", serial, "shell", &start_cmd])
            .status()
            .await
            .map_err(|e| DaemonError::Storage(format!("Failed to start sshd on device: {}", e)))?;

        if !start_status.success() {
            return Err(DaemonError::Storage("Failed to spawn OpenSSH daemon on phone".into()));
        }
        tokio::time::sleep(std::time::Duration::from_millis(150)).await;
    }

    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn ensure_device_storage_ready_fails_on_bogus_serial() {
        // Calling with an unreachable serial will error out cleanly without panic
        let rt = tokio::runtime::Runtime::new().unwrap();
        rt.block_on(async {
            let res = ensure_device_storage_ready("127.0.0.1:59999", 2222).await;
            assert!(res.is_err());
        });
    }
}
