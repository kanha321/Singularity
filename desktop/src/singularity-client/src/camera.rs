use singularity_protocol::{CameraConfig, CameraLens, CameraStatus, IpcRequest, IpcResponse};
use crate::client::SingularityClient;
use crate::error::{ClientError, Result};

impl SingularityClient {
    pub async fn start_camera(&self, config: Option<CameraConfig>) -> Result<CameraStatus> {
        match self.send(&IpcRequest::StartCamera { config }).await? {
            IpcResponse::CameraStatus(s) => Ok(s),
            IpcResponse::Error(e) => Err(ClientError::DaemonError(e)),
            _ => Err(ClientError::DaemonError("Unexpected response for start camera".into())),
        }
    }

    pub async fn stop_camera(&self) -> Result<()> {
        match self.send(&IpcRequest::StopCamera).await? {
            IpcResponse::Ok => Ok(()),
            IpcResponse::Error(e) => Err(ClientError::DaemonError(e)),
            _ => Err(ClientError::DaemonError("Unexpected response for stop camera".into())),
        }
    }

    pub async fn get_camera_status(&self) -> Result<CameraStatus> {
        match self.send(&IpcRequest::GetCameraStatus).await? {
            IpcResponse::CameraStatus(s) => Ok(s),
            IpcResponse::Error(e) => Err(ClientError::DaemonError(e)),
            _ => Err(ClientError::DaemonError("Unexpected response for camera status".into())),
        }
    }

    pub async fn switch_camera_lens(&self, lens: CameraLens) -> Result<CameraStatus> {
        match self.send(&IpcRequest::SwitchCameraLens { lens }).await? {
            IpcResponse::CameraStatus(s) => Ok(s),
            IpcResponse::Error(e) => Err(ClientError::DaemonError(e)),
            _ => Err(ClientError::DaemonError("Unexpected response for switch lens".into())),
        }
    }

    pub async fn set_camera_torch(&self, enabled: bool) -> Result<()> {
        match self.send(&IpcRequest::SetCameraTorch { enabled }).await? {
            IpcResponse::Ok => Ok(()),
            IpcResponse::Error(e) => Err(ClientError::DaemonError(e)),
            _ => Err(ClientError::DaemonError("Unexpected response for set torch".into())),
        }
    }

    pub async fn set_camera_zoom(&self, zoom: f32) -> Result<()> {
        match self.send(&IpcRequest::SetCameraZoom { zoom }).await? {
            IpcResponse::Ok => Ok(()),
            IpcResponse::Error(e) => Err(ClientError::DaemonError(e)),
            _ => Err(ClientError::DaemonError("Unexpected response for set zoom".into())),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn camera_client_methods_instantiate() {
        let client = SingularityClient::new();
        assert!(client.socket_path.to_string_lossy().contains("singularity.sock"));
    }
}
