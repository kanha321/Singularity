use std::path::{Path, PathBuf};
use tokio::net::UnixStream;
use singularity_protocol::{FeatureStatus, IpcRequest, IpcResponse};
use crate::error::{ClientError, Result};
use crate::framed::{read_framed, write_framed};

pub struct SingularityClient {
    socket_path: PathBuf,
}

impl SingularityClient {
    pub fn new() -> Self {
        let uid = std::env::var("UID").unwrap_or_else(|_| "1000".into());
        Self { socket_path: PathBuf::from(format!("/run/user/{}/singularity.sock", uid)) }
    }

    pub fn with_socket_path<P: AsRef<Path>>(path: P) -> Self {
        Self { socket_path: path.as_ref().to_path_buf() }
    }

    async fn connect(&self) -> Result<UnixStream> {
        UnixStream::connect(&self.socket_path).await
            .map_err(|e| ClientError::ConnectionFailed(self.socket_path.display().to_string(), e))
    }

    pub async fn send(&self, request: &IpcRequest) -> Result<IpcResponse> {
        let mut stream = self.connect().await?;
        write_framed(&mut stream, request).await?;
        read_framed(&mut stream).await
    }

    pub async fn get_status(&self) -> Result<Vec<FeatureStatus>> {
        match self.send(&IpcRequest::GetStatus).await? {
            IpcResponse::Status(s) => Ok(s),
            IpcResponse::Error(e) => Err(ClientError::DaemonError(e)),
            _ => Err(ClientError::DaemonError("Unexpected response".into())),
        }
    }

    pub async fn set_clipboard(&self, text: &str) -> Result<()> {
        match self.send(&IpcRequest::SetClipboard { text: text.into() }).await? {
            IpcResponse::Ok => Ok(()),
            IpcResponse::Error(e) => Err(ClientError::DaemonError(e)),
            _ => Err(ClientError::DaemonError("Unexpected response".into())),
        }
    }

    pub async fn accept_call(&self) -> Result<()> {
        match self.send(&IpcRequest::AcceptCall).await? {
            IpcResponse::Ok => Ok(()),
            IpcResponse::Error(e) => Err(ClientError::DaemonError(e)),
            _ => Err(ClientError::DaemonError("Unexpected response".into())),
        }
    }

    pub async fn end_call(&self) -> Result<()> {
        match self.send(&IpcRequest::EndCall).await? {
            IpcResponse::Ok => Ok(()),
            IpcResponse::Error(e) => Err(ClientError::DaemonError(e)),
            _ => Err(ClientError::DaemonError("Unexpected response".into())),
        }
    }

    pub async fn mount_storage(&self, remote_path: Option<String>) -> Result<String> {
        match self.send(&IpcRequest::MountStorage { remote_path }).await? {
            IpcResponse::StorageMounted { mount_point } => Ok(mount_point),
            IpcResponse::Error(e) => Err(ClientError::DaemonError(e)),
            _ => Err(ClientError::DaemonError("Unexpected response".into())),
        }
    }

    pub async fn unmount_storage(&self) -> Result<()> {
        match self.send(&IpcRequest::UnmountStorage).await? {
            IpcResponse::StorageUnmounted | IpcResponse::Ok => Ok(()),
            IpcResponse::Error(e) => Err(ClientError::DaemonError(e)),
            _ => Err(ClientError::DaemonError("Unexpected response".into())),
        }
    }

    pub async fn authenticate_biometric_streaming<F>(
        &self,
        reason: &str,
        timeout_secs: u32,
        mut on_progress: F,
    ) -> Result<bool>
    where
        F: FnMut(singularity_protocol::AuthProgressEvent),
    {
        let mut stream = self.connect().await?;
        let req = IpcRequest::AuthenticateBiometric { reason: reason.to_string(), timeout_secs };
        write_framed(&mut stream, &req).await?;

        loop {
            match read_framed(&mut stream).await? {
                IpcResponse::AuthProgress(event) => on_progress(event),
                IpcResponse::AuthResult { success, .. } => return Ok(success),
                IpcResponse::Error(msg) => return Err(ClientError::DaemonError(msg)),
                _ => return Err(ClientError::DaemonError("Unexpected auth response".into())),
            }
        }
    }

    pub async fn authenticate_biometric(&self, reason: &str, timeout_secs: u32) -> Result<bool> {
        self.authenticate_biometric_streaming(reason, timeout_secs, |_| {}).await
    }

    pub async fn cancel_biometric(&self) -> Result<()> {
        match self.send(&IpcRequest::CancelBiometric).await? {
            IpcResponse::Ok => Ok(()),
            IpcResponse::Error(e) => Err(ClientError::DaemonError(e)),
            _ => Err(ClientError::DaemonError("Unexpected response".into())),
        }
    }

    pub async fn open_url(&self, url: &str) -> Result<()> {
        match self.send(&IpcRequest::OpenUrl { url: url.into() }).await? {
            IpcResponse::Ok => Ok(()),
            IpcResponse::Error(e) => Err(ClientError::DaemonError(e)),
            _ => Err(ClientError::DaemonError("Unexpected response".into())),
        }
    }
}

impl Default for SingularityClient {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn default_client_resolves_run_user_socket_path() {
        let client = SingularityClient::new();
        assert!(client.socket_path.to_str().unwrap().contains("singularity.sock"));
    }
}
