use async_trait::async_trait;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use crate::bridge::Bridge;
use crate::error::{CoreError, Result};

#[async_trait]
pub trait ShellCommandRunner: Send + Sync {
    async fn exec_command(&self, command: &str) -> Result<String>;
}

/// Specialized bridge for generic UID 2000 (Shell / ADB) operations.
pub struct ShellBridge {
    is_active: AtomicBool,
    runner: Option<Arc<dyn ShellCommandRunner>>,
}

impl ShellBridge {
    pub fn new() -> Self {
        Self {
            is_active: AtomicBool::new(true),
            runner: None,
        }
    }

    pub fn with_runner(runner: Arc<dyn ShellCommandRunner>) -> Self {
        Self {
            is_active: AtomicBool::new(true),
            runner: Some(runner),
        }
    }

    /// Executes an AOSP platform CLI command under UID 2000 (e.g. telecom, am, pm)
    pub async fn exec_command(&self, command: &str) -> Result<String> {
        if !self.is_active.load(Ordering::Relaxed) {
            return Err(CoreError::BridgeFailure {
                bridge: self.name(),
                message: "Bridge is shut down".into(),
            });
        }
        if let Some(runner) = &self.runner {
            return runner.exec_command(command).await;
        }
        tracing::debug!(target: "shell_bridge", command, "Executing shell command (mock)");
        Ok(String::new())
    }

    /// Sends a raw control packet to scrcpy-server or device forwarder
    pub async fn send_control(&self, packet: &[u8]) -> Result<()> {
        if !self.is_active.load(Ordering::Relaxed) {
            return Err(CoreError::BridgeFailure {
                bridge: self.name(),
                message: "Bridge is shut down".into(),
            });
        }
        tracing::debug!(target: "shell_bridge", len = packet.len(), "Dispatching control packet");
        Ok(())
    }
}

impl Default for ShellBridge {
    fn default() -> Self {
        Self::new()
    }
}

#[async_trait]
impl Bridge for ShellBridge {
    fn name(&self) -> &'static str {
        "ShellBridge"
    }

    async fn is_healthy(&self) -> bool {
        self.is_active.load(Ordering::Relaxed)
    }

    async fn shutdown(&self) -> Result<()> {
        self.is_active.store(false, Ordering::Relaxed);
        tracing::info!(target: "shell_bridge", "ShellBridge gracefully shut down");
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    struct FakeRunner;
    #[async_trait]
    impl ShellCommandRunner for FakeRunner {
        async fn exec_command(&self, command: &str) -> Result<String> {
            Ok(format!("executed: {}", command))
        }
    }

    #[tokio::test]
    async fn shell_bridge_verifies_active_state_for_commands() {
        let bridge = ShellBridge::new();
        assert_eq!(bridge.name(), "ShellBridge");
        assert!(bridge.exec_command("echo test").await.is_ok());
        assert!(bridge.send_control(&[0x09, 0x00]).await.is_ok());

        bridge.shutdown().await.unwrap();
        assert!(bridge.exec_command("echo test").await.is_err());
        assert!(bridge.send_control(&[0x09]).await.is_err());
    }

    #[tokio::test]
    async fn shell_bridge_with_runner_delegates_command() {
        let bridge = ShellBridge::with_runner(Arc::new(FakeRunner));
        let res = bridge.exec_command("pm list").await.unwrap();
        assert_eq!(res, "executed: pm list");
    }
}
