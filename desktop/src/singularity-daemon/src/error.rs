use thiserror::Error;

#[derive(Error, Debug)]
pub enum DaemonError {
    #[error("I/O error: {0}")]
    Io(#[from] std::io::Error),

    #[error("Core engine error: {0}")]
    Core(#[from] singularity_core::CoreError),

    #[error("Protocol error: {0}")]
    Protocol(#[from] singularity_protocol::ProtocolError),

    #[error("Failed to bind UNIX domain socket at '{0}': {1}")]
    SocketBindFailed(String, std::io::Error),

    #[error("Wayland paste/watch error: {0}")]
    WaylandPaste(#[from] wl_clipboard_rs::paste::Error),

    #[error("Wayland copy error: {0}")]
    WaylandCopy(#[from] wl_clipboard_rs::copy::Error),

    #[error("ADB tunnel error: {0}")]
    AdbTunnel(String),

    #[error("Storage error: {0}")]
    Storage(String),

    #[error("Singleton violation: {0}")]
    AlreadyRunning(String),
}

pub type Result<T> = std::result::Result<T, DaemonError>;

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn socket_bind_error_displays_path() {
        let err = DaemonError::SocketBindFailed("/tmp/sock".into(), std::io::Error::new(std::io::ErrorKind::AddrInUse, "in use"));
        assert!(err.to_string().contains("/tmp/sock"));
    }
}
