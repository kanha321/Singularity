use std::fs::{File, OpenOptions};
use std::os::unix::fs::OpenOptionsExt;
use std::os::unix::io::AsRawFd;
use std::path::{Path, PathBuf};
use crate::error::{DaemonError, Result};

/// Enforces that only a single instance of `singularity-daemon` can run at any time.
/// Uses an OS kernel advisory lock (`flock`) on `<socket_path>.lock`.
#[derive(Debug)]
pub struct SingletonLock {
    _file: File,
    lock_path: PathBuf,
}

impl SingletonLock {
    /// Attempts to acquire an exclusive lock. Fails immediately if another instance holds it.
    pub fn acquire<P: AsRef<Path>>(socket_path: P) -> Result<Self> {
        let socket_path = socket_path.as_ref();
        let lock_path = socket_path.with_extension("lock");

        if let Some(parent) = lock_path.parent() {
            let _ = std::fs::create_dir_all(parent);
        }

        let file = OpenOptions::new()
            .read(true)
            .write(true)
            .create(true)
            .truncate(false)
            .mode(0o600)
            .open(&lock_path)
            .map_err(DaemonError::Io)?;

        let fd = file.as_raw_fd();
        let ret = unsafe { libc::flock(fd, libc::LOCK_EX | libc::LOCK_NB) };
        if ret != 0 {
            let err = std::io::Error::last_os_error();
            return Err(DaemonError::AlreadyRunning(format!(
                "Another instance of singularity-daemon is already running (locked '{}': {})",
                lock_path.display(),
                err
            )));
        }

        // Check if an existing socket file has an active listening daemon
        if socket_path.exists() {
            if std::os::unix::net::UnixStream::connect(socket_path).is_ok() {
                return Err(DaemonError::AlreadyRunning(format!(
                    "Active daemon listener responding at '{}'",
                    socket_path.display()
                )));
            } else {
                // Stale socket from previous crash/abrupt exit; safe to clean up
                let _ = std::fs::remove_file(socket_path);
            }
        }

        tracing::info!(target: "daemon", lock = %lock_path.display(), "Acquired exclusive singleton daemon lock");

        Ok(Self {
            _file: file,
            lock_path,
        })
    }
}

impl Drop for SingletonLock {
    fn drop(&mut self) {
        let _ = std::fs::remove_file(&self.lock_path);
        tracing::debug!(target: "daemon", "Released singleton lock");
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn singleton_prevents_duplicate_acquisition() {
        let temp_dir = tempfile::tempdir().unwrap();
        let sock_path = temp_dir.path().join("test.sock");

        let lock1 = SingletonLock::acquire(&sock_path);
        assert!(lock1.is_ok());

        let lock2 = SingletonLock::acquire(&sock_path);
        assert!(lock2.is_err());
        assert!(matches!(lock2.unwrap_err(), DaemonError::AlreadyRunning(_)));

        drop(lock1);

        let lock3 = SingletonLock::acquire(&sock_path);
        assert!(lock3.is_ok());
    }
}
