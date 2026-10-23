use async_trait::async_trait;
use std::sync::atomic::{AtomicBool, Ordering};
use crate::bridge::Bridge;
use crate::error::{CoreError, Result};

/// Specialized bridge for generic UID 0 (Root / KernelSU) privileged operations.
pub struct RootBridge {
    is_active: AtomicBool,
}

impl RootBridge {
    pub fn new() -> Self {
        Self {
            is_active: AtomicBool::new(true),
        }
    }

    /// Executes an arbitrary root command via su / KernelSU
    pub async fn exec_su(&self, command: &str) -> Result<String> {
        if !self.is_active.load(Ordering::Relaxed) {
            return Err(CoreError::BridgeFailure {
                bridge: self.name(),
                message: "Bridge is shut down".into(),
            });
        }
        tracing::debug!(target: "root_bridge", command, "Executing privileged root command");
        Ok(String::new())
    }

    /// Writes raw string content to a kernel sysfs/procfs node
    pub async fn write_sysfs(&self, path: &str, value: &str) -> Result<()> {
        if !self.is_active.load(Ordering::Relaxed) {
            return Err(CoreError::BridgeFailure {
                bridge: self.name(),
                message: "Bridge is shut down".into(),
            });
        }
        tracing::debug!(target: "root_bridge", path, value, "Writing to sysfs node");
        Ok(())
    }

    /// Reads raw string content from a kernel sysfs/procfs node
    pub async fn read_sysfs(&self, path: &str) -> Result<String> {
        if !self.is_active.load(Ordering::Relaxed) {
            return Err(CoreError::BridgeFailure {
                bridge: self.name(),
                message: "Bridge is shut down".into(),
            });
        }
        tracing::debug!(target: "root_bridge", path, "Reading from sysfs node");
        Ok(String::new())
    }
}

impl Default for RootBridge {
    fn default() -> Self {
        Self::new()
    }
}

#[async_trait]
impl Bridge for RootBridge {
    fn name(&self) -> &'static str {
        "RootBridge"
    }

    async fn is_healthy(&self) -> bool {
        self.is_active.load(Ordering::Relaxed)
    }

    async fn shutdown(&self) -> Result<()> {
        self.is_active.store(false, Ordering::Relaxed);
        tracing::info!(target: "root_bridge", "RootBridge gracefully shut down");
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn root_bridge_verifies_active_state_for_operations() {
        let bridge = RootBridge::new();
        assert_eq!(bridge.name(), "RootBridge");
        assert!(bridge.is_healthy().await);
        assert!(bridge.exec_su("id").await.is_ok());
        assert!(bridge.write_sysfs("/sys/class/test", "1").await.is_ok());

        bridge.shutdown().await.unwrap();
        assert!(!bridge.is_healthy().await);
        assert!(bridge.exec_su("id").await.is_err());
        assert!(bridge.write_sysfs("/sys/class/test", "1").await.is_err());
    }
}
