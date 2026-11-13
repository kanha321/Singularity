use singularity_protocol::{IpcRequest, IpcResponse};
use crate::client::SingularityClient;
use crate::error::{ClientError, Result};

impl SingularityClient {
    /// Dispatches Find My Device alert (ring: true to start, ring: false to stop).
    pub async fn find_device(&self, ring: bool) -> Result<bool> {
        match self.send(&IpcRequest::FindDevice { ring }).await? {
            IpcResponse::FindDeviceResult { ringing } => Ok(ringing),
            IpcResponse::Ok => Ok(ring),
            IpcResponse::Error(e) => Err(ClientError::DaemonError(e)),
            _ => Err(ClientError::DaemonError("Unexpected response for find device".into())),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_find_device_method_structure() {
        let client = SingularityClient::new();
        let _ = client;
    }
}

