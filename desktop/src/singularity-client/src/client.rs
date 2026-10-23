use std::path::{Path, PathBuf};
use tokio::net::UnixStream;
use tokio::io::{AsyncReadExt, AsyncWriteExt};
use singularity_protocol::{FeatureStatus, IpcRequest, IpcResponse};
use crate::error::{ClientError, Result};

pub struct SingularityClient {
    socket_path: PathBuf,
}

impl SingularityClient {
    pub fn new() -> Self {
        let uid = std::env::var("UID")
            .or_else(|_| std::env::var("USER").map(|_| "1000".into()))
            .unwrap_or_else(|_| "1000".into());
        let socket_path = PathBuf::from(format!("/run/user/{}/singularity.sock", uid));
        Self { socket_path }
    }

    pub fn with_socket_path<P: AsRef<Path>>(path: P) -> Self {
        Self { socket_path: path.as_ref().to_path_buf() }
    }

    pub async fn send(&self, request: &IpcRequest) -> Result<IpcResponse> {
        let mut stream = UnixStream::connect(&self.socket_path)
            .await
            .map_err(|e| ClientError::ConnectionFailed(self.socket_path.display().to_string(), e))?;

        let payload = serde_json::to_vec(request)?;
        let len_bytes = (payload.len() as u32).to_be_bytes();

        stream.write_all(&len_bytes).await?;
        stream.write_all(&payload).await?;
        stream.flush().await?;

        let mut resp_len_buf = [0u8; 4];
        stream.read_exact(&mut resp_len_buf).await?;
        let resp_len = u32::from_be_bytes(resp_len_buf) as usize;

        let mut resp_buf = vec![0u8; resp_len];
        stream.read_exact(&mut resp_buf).await?;

        let response: IpcResponse = serde_json::from_slice(&resp_buf)?;
        Ok(response)
    }

    pub async fn get_status(&self) -> Result<Vec<FeatureStatus>> {
        match self.send(&IpcRequest::GetStatus).await? {
            IpcResponse::Status(statuses) => Ok(statuses),
            IpcResponse::Error(msg) => Err(ClientError::DaemonError(msg)),
            _ => Err(ClientError::DaemonError("Unexpected response for GetStatus".into())),
        }
    }

    pub async fn set_clipboard(&self, text: &str) -> Result<()> {
        match self.send(&IpcRequest::SetClipboard { text: text.into() }).await? {
            IpcResponse::Ok => Ok(()),
            IpcResponse::Error(msg) => Err(ClientError::DaemonError(msg)),
            _ => Err(ClientError::DaemonError("Unexpected response".into())),
        }
    }

    pub async fn accept_call(&self) -> Result<()> {
        match self.send(&IpcRequest::AcceptCall).await? {
            IpcResponse::Ok => Ok(()),
            IpcResponse::Error(msg) => Err(ClientError::DaemonError(msg)),
            _ => Err(ClientError::DaemonError("Unexpected response".into())),
        }
    }

    pub async fn end_call(&self) -> Result<()> {
        match self.send(&IpcRequest::EndCall).await? {
            IpcResponse::Ok => Ok(()),
            IpcResponse::Error(msg) => Err(ClientError::DaemonError(msg)),
            _ => Err(ClientError::DaemonError("Unexpected response".into())),
        }
    }

    pub async fn mount_storage(&self, remote_path: Option<String>) -> Result<String> {
        match self.send(&IpcRequest::MountStorage { remote_path }).await? {
            IpcResponse::StorageMounted { mount_point } => Ok(mount_point),
            IpcResponse::Error(msg) => Err(ClientError::DaemonError(msg)),
            _ => Err(ClientError::DaemonError("Unexpected response for MountStorage".into())),
        }
    }

    pub async fn unmount_storage(&self) -> Result<()> {
        match self.send(&IpcRequest::UnmountStorage).await? {
            IpcResponse::StorageUnmounted | IpcResponse::Ok => Ok(()),
            IpcResponse::Error(msg) => Err(ClientError::DaemonError(msg)),
            _ => Err(ClientError::DaemonError("Unexpected response for UnmountStorage".into())),
        }
    }

    pub async fn authenticate_biometric(&self, reason: &str, timeout_secs: u32) -> Result<bool> {
        let req = IpcRequest::AuthenticateBiometric {
            reason: reason.to_string(),
            timeout_secs,
        };
        match self.send(&req).await? {
            IpcResponse::AuthResult { success, .. } => Ok(success),
            IpcResponse::Error(msg) => Err(ClientError::DaemonError(msg)),
            _ => Err(ClientError::DaemonError("Unexpected response for AuthenticateBiometric".into())),
        }
    }

    pub async fn cancel_biometric(&self) -> Result<()> {
        match self.send(&IpcRequest::CancelBiometric).await? {
            IpcResponse::Ok => Ok(()),
            IpcResponse::Error(msg) => Err(ClientError::DaemonError(msg)),
            _ => Err(ClientError::DaemonError("Unexpected response for CancelBiometric".into())),
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
