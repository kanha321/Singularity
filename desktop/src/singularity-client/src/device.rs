use singularity_protocol::{DeviceEntry, IpcRequest, IpcResponse};
use crate::client::SingularityClient;
use crate::error::{ClientError, Result};

impl SingularityClient {
    pub async fn list_devices(&self) -> Result<Vec<DeviceEntry>> {
        match self.send(&IpcRequest::ListDevices).await? {
            IpcResponse::Devices(devices) => Ok(devices),
            IpcResponse::Error(e) => Err(ClientError::DaemonError(e)),
            _ => Err(ClientError::DaemonError("Unexpected response for list devices".into())),
        }
    }

    pub async fn select_device(&self, serial: &str) -> Result<()> {
        match self.send(&IpcRequest::SelectDevice { serial: serial.into() }).await? {
            IpcResponse::Ok => Ok(()),
            IpcResponse::Error(e) => Err(ClientError::DaemonError(e)),
            _ => Err(ClientError::DaemonError("Unexpected response for select device".into())),
        }
    }

    pub async fn pair_device(&self, serial: &str) -> Result<()> {
        match self.send(&IpcRequest::PairDevice { serial: serial.into() }).await? {
            IpcResponse::Ok => Ok(()),
            IpcResponse::Error(e) => Err(ClientError::DaemonError(e)),
            _ => Err(ClientError::DaemonError("Unexpected response for pair device".into())),
        }
    }

    pub async fn unpair_device(&self, serial: &str) -> Result<()> {
        match self.send(&IpcRequest::UnpairDevice { serial: serial.into() }).await? {
            IpcResponse::Ok => Ok(()),
            IpcResponse::Error(e) => Err(ClientError::DaemonError(e)),
            _ => Err(ClientError::DaemonError("Unexpected response for unpair device".into())),
        }
    }
}

