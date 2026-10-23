use serde::{Deserialize, Serialize};
use crate::event::DeviceEvent;
use crate::status::FeatureStatus;

/// Request sent by clients (GUI/CLI) to the background daemon.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "action", content = "payload")]
pub enum IpcRequest {
    GetStatus,
    SetClipboard { text: String },
    AcceptCall,
    EndCall,
    MountStorage { remote_path: Option<String> },
    UnmountStorage,
    AuthenticateBiometric {
        reason: String,
        timeout_secs: u32,
    },
    CancelBiometric,
    Custom {
        feature: String,
        action: String,
        payload: serde_json::Value,
    },
}

/// Response returned by the background daemon to a requesting client.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "status", content = "data")]
pub enum IpcResponse {
    Ok,
    Status(Vec<FeatureStatus>),
    StorageMounted { mount_point: String },
    StorageUnmounted,
    AuthResult {
        success: bool,
        reason: Option<String>,
    },
    Custom(serde_json::Value),
    Error(String),
}

/// Asynchronous notification broadcast from the daemon to connected clients.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "type", content = "data")]
pub enum IpcEvent {
    Device(DeviceEvent),
    DaemonShutdown,
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn ipc_request_serialization_roundtrip() {
        let req = IpcRequest::SetClipboard {
            text: "sync text".into(),
        };
        let json = serde_json::to_string(&req).unwrap();
        assert!(json.contains("\"action\":\"SetClipboard\""));
    }

    #[test]
    fn ipc_response_serialization_roundtrip() {
        let resp = IpcResponse::Error("Device disconnected".into());
        let json = serde_json::to_string(&resp).unwrap();
        assert!(json.contains("\"status\":\"Error\""));
    }

    #[test]
    fn authenticate_biometric_serialization_roundtrip() {
        let req = IpcRequest::AuthenticateBiometric {
            reason: "sudo verification".into(),
            timeout_secs: 10,
        };
        let json = serde_json::to_string(&req).unwrap();
        assert!(json.contains("AuthenticateBiometric"));

        let resp = IpcResponse::AuthResult {
            success: true,
            reason: None,
        };
        let resp_json = serde_json::to_string(&resp).unwrap();
        assert!(resp_json.contains("AuthResult"));

        let cancel = IpcRequest::CancelBiometric;
        let cancel_json = serde_json::to_string(&cancel).unwrap();
        assert!(cancel_json.contains("CancelBiometric"));
    }
}
