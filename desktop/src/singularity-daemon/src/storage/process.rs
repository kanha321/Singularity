use std::path::Path;
use std::process::Command;
use crate::error::{DaemonError, Result};

pub fn check_madbfs_installed() -> Result<()> {
    match Command::new("which").arg("madbfs").output() {
        Ok(out) if out.status.success() => Ok(()),
        _ => Err(DaemonError::Storage(
            "madbfs client not found on host. Install via: yay -S madbfs-bin".into(),
        )),
    }
}

pub fn mount_phone<P: AsRef<Path>>(
    serial: &str,
    remote_path: &str,
    mount_point: P,
) -> Result<()> {
    check_madbfs_installed()?;

    let mount_point = mount_point.as_ref();
    let _ = Command::new("fusermount3")
        .args(["-u", "-z", mount_point.to_string_lossy().as_ref()])
        .status();

    if !mount_point.exists() {
        std::fs::create_dir_all(mount_point).map_err(|e| {
            DaemonError::Storage(format!("Failed to create mount directory: {}", e))
        })?;
    }

    let root_path = if remote_path.trim().is_empty() {
        "/sdcard"
    } else {
        remote_path.trim()
    };

    tracing::info!(
        target: "storage_process",
        serial,
        root_path,
        mount_point = %mount_point.display(),
        "Invoking madbfs to mount phone storage over ADB"
    );

    let mut cmd = Command::new("madbfs");
    let trimmed_serial = serial.trim();
    if !trimmed_serial.is_empty() && trimmed_serial != "auto" {
        cmd.arg(format!("--serial={}", trimmed_serial));
    }
    cmd.arg(format!("--root={}", root_path));
    cmd.arg(mount_point.to_string_lossy().as_ref());

    let status = cmd
        .status()
        .map_err(|e| DaemonError::Storage(format!("Failed executing madbfs: {}", e)))?;

    if !status.success() {
        return Err(DaemonError::Storage(format!(
            "madbfs failed with exit code: {:?}",
            status.code()
        )));
    }

    tracing::info!(
        target: "storage_process",
        mount_point = %mount_point.display(),
        "Phone storage successfully mounted via ADB FUSE"
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

    #[test]
    fn check_madbfs_installed_verifies_presence() {
        assert!(check_madbfs_installed().is_ok());
    }
}
