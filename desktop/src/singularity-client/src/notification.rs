use singularity_protocol::{IpcRequest, IpcResponse};
use crate::client::SingularityClient;
use crate::error::{ClientError, Result};

impl SingularityClient {
    pub async fn reply_notification(&self, id: &str, text: &str) -> Result<()> {
        match self.send(&IpcRequest::NotificationReply { id: id.into(), text: text.into() }).await? {
            IpcResponse::Ok => Ok(()),
            IpcResponse::Error(e) => Err(ClientError::DaemonError(e)),
            _ => Err(ClientError::DaemonError("Unexpected response for reply notification".into())),
        }
    }

    pub async fn invoke_notification_action(&self, id: &str, action_id: &str) -> Result<()> {
        match self.send(&IpcRequest::NotificationAction { id: id.into(), action_id: action_id.into() }).await? {
            IpcResponse::Ok => Ok(()),
            IpcResponse::Error(e) => Err(ClientError::DaemonError(e)),
            _ => Err(ClientError::DaemonError("Unexpected response for notification action".into())),
        }
    }

    pub async fn list_notifications(&self) -> Result<Vec<singularity_protocol::PhoneNotification>> {
        match self.send(&IpcRequest::ListNotifications).await? {
            IpcResponse::Notifications(n) => Ok(n),
            IpcResponse::Error(e) => Err(ClientError::DaemonError(e)),
            _ => Err(ClientError::DaemonError("Unexpected response for list notifications".into())),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_notification_client_structure() {
        let client = SingularityClient::new();
        assert!(!client.socket_path.as_os_str().is_empty());
    }
}

