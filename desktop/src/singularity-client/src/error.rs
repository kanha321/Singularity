use thiserror::Error;

#[derive(Error, Debug)]
pub enum ClientError {
    #[error("Failed to connect to Singularity daemon at '{0}': {1}")]
    ConnectionFailed(String, std::io::Error),

    #[error("I/O error during daemon communication: {0}")]
    Io(#[from] std::io::Error),

    #[error("Serialization / frame codec error: {0}")]
    Serialization(#[from] serde_json::Error),

    #[error("Daemon returned error: {0}")]
    DaemonError(String),

    #[error("Connection closed unexpectedly")]
    Disconnected,
}

pub type Result<T> = std::result::Result<T, ClientError>;

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn daemon_error_displays_message() {
        let err = ClientError::DaemonError("Bridge offline".into());
        assert_eq!(err.to_string(), "Daemon returned error: Bridge offline");
    }
}
