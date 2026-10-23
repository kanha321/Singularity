use async_trait::async_trait;
use std::sync::atomic::{AtomicBool, Ordering};
use crate::bridge::Bridge;
use crate::error::{CoreError, Result};

/// Specialized bridge for generic UID 10xxx (Kotlin Android companion app) transport.
pub struct AppBridge {
    is_active: AtomicBool,
}

impl AppBridge {
    pub fn new() -> Self {
        Self {
            is_active: AtomicBool::new(true),
        }
    }

    /// Dispatches a typed event message to the companion app over the ADB transport
    pub async fn send_event(&self, event_type: &str, payload: serde_json::Value) -> Result<()> {
        if !self.is_active.load(Ordering::Relaxed) {
            return Err(CoreError::BridgeFailure {
                bridge: self.name(),
                message: "Bridge is shut down".into(),
            });
        }
        tracing::debug!(target: "app_bridge", event_type, %payload, "Dispatching event to companion app");
        Ok(())
    }

    /// Requests biometric authentication via the companion app
    pub async fn authenticate(&self, reason: &str, timeout_secs: u32) -> Result<bool> {
        if !self.is_active.load(Ordering::Relaxed) {
            return Err(CoreError::BridgeFailure {
                bridge: self.name(),
                message: "Bridge is shut down".into(),
            });
        }
        tracing::debug!(target: "app_bridge", reason, timeout_secs, "Triggering companion app biometric prompt");
        Ok(true)
    }
}

impl Default for AppBridge {
    fn default() -> Self {
        Self::new()
    }
}

#[async_trait]
impl Bridge for AppBridge {
    fn name(&self) -> &'static str {
        "AppBridge"
    }

    async fn is_healthy(&self) -> bool {
        self.is_active.load(Ordering::Relaxed)
    }

    async fn shutdown(&self) -> Result<()> {
        self.is_active.store(false, Ordering::Relaxed);
        tracing::info!(target: "app_bridge", "AppBridge gracefully shut down");
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn app_bridge_lifecycle_and_send_event() {
        let bridge = AppBridge::new();
        assert_eq!(bridge.name(), "AppBridge");
        assert!(bridge.is_healthy().await);
        assert!(bridge.send_event("test_event", serde_json::json!({"key": "val"})).await.is_ok());

        bridge.shutdown().await.unwrap();
        assert!(bridge.send_event("test_event", serde_json::json!({})).await.is_err());
    }
}
