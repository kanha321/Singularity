use std::path::Path;
use std::process::Command;
use crate::error::{DaemonError, Result};

pub fn check_sshfs_installed() -> Result<()> {
    match Command::new("which").arg("sshfs").output() {
        Ok(out) if out.status.success() => Ok(()),
        _ => Err(DaemonError::Storage(
            "sshfs client not found on host. Please install it with: sudo pacman -S sshfs".into(),
        )),
    }
}

pub fn mount_phone<P: AsRef<Path>>(
    local_port: u16,
    remote_path: &str,
    mount_point: P,
    identity_file: P,
) -> Result<()> {
    check_sshfs_installed()?;

    let mount_point = mount_point.as_ref();
    let identity_file = identity_file.as_ref();

    // Preemptively clean up any stale FUSE handle from a previous crashed session
    let _ = Command::new("fusermount3")
        .args(["-u", "-z", mount_point.to_string_lossy().as_ref()])
        .status();

    if !mount_point.exists() {
        std::fs::create_dir_all(mount_point).map_err(|e| {
            DaemonError::Storage(format!("Failed to create mount directory: {}", e))
        })?;
    }

    let clean_path = remote_path.trim_start_matches('/');
    let source = if clean_path.is_empty() {
        "root@127.0.0.1://".to_string()
    } else {
        format!("root@127.0.0.1://{}", clean_path)
    };
    let port_str = local_port.to_string();

    tracing::info!(
        target: "storage_process",
        local_port,
        remote_path,
        mount_point = %mount_point.display(),
        "Invoking sshfs to mount remote phone storage"
    );

    let status = Command::new("sshfs")
        .args([
            &source,
            mount_point.to_string_lossy().as_ref(),
            "-p", &port_str,
            "-o", &format!("IdentityFile={}", identity_file.display()),
            "-o", "StrictHostKeyChecking=no",
            "-o", "UserKnownHostsFile=/dev/null",
            "-o", "reconnect,ServerAliveInterval=15,ServerAliveCountMax=3,follow_symlinks",
        ])
        .status()
        .map_err(|e| DaemonError::Storage(format!("Failed executing sshfs: {}", e)))?;

    if !status.success() {
        return Err(DaemonError::Storage(format!(
            "sshfs failed with exit code: {:?}",
            status.code()
        )));
    }

    tracing::info!(
        target: "storage_process",
        mount_point = %mount_point.display(),
        "Phone storage successfully mounted"
    );
    Ok(())
}

pub fn unmount_phone<P: AsRef<Path>>(mount_point: P) -> Result<()> {
    let mount_point = mount_point.as_ref();
    if !mount_point.exists() {
        return Ok(());
    }

    tracing::info!(
        target: "storage_process",
        mount_point = %mount_point.display(),
        "Unmounting phone storage with fusermount3"
    );

    let _ = Command::new("fusermount3")
        .args(["-u", "-z", mount_point.to_string_lossy().as_ref()])
        .status();

    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn unmount_non_existent_path_returns_ok() {
        let fake_path = Path::new("/tmp/non_existent_singularity_mount_test_123");
        assert!(unmount_phone(fake_path).is_ok());
    }
}
