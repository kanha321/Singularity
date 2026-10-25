use async_trait::async_trait;
use tokio::process::Command;
use singularity_core::bridge::{RootCommandRunner, ShellCommandRunner};
use singularity_core::error::{CoreError, Result};

pub struct AdbRootRunner {
    pub(crate) serial: String,
}

impl AdbRootRunner {
    pub fn new(serial: impl Into<String>) -> Self {
        Self { serial: serial.into() }
    }
}

#[async_trait]
impl RootCommandRunner for AdbRootRunner {
    async fn exec_su(&self, command: &str) -> Result<String> {
        let su_cmd = format!("su -mm -c '{}'", command.replace('\'', "'\\''"));
        let output = Command::new("adb")
            .args(["-s", &self.serial, "shell", &su_cmd])
            .output()
            .await
            .map_err(|e| CoreError::BridgeFailure {
                bridge: "RootBridge",
                message: format!("Failed executing adb su command: {}", e),
            })?;

        if !output.status.success() {
            let stderr = String::from_utf8_lossy(&output.stderr);
            let stdout = String::from_utf8_lossy(&output.stdout);
            return Err(CoreError::BridgeFailure {
                bridge: "RootBridge",
                message: format!(
                    "su command returned code {:?}: {} {}",
                    output.status.code(),
                    stdout.trim(),
                    stderr.trim()
                ),
            });
        }

        Ok(String::from_utf8_lossy(&output.stdout).to_string())
    }
}

pub struct AdbShellRunner {
    pub(crate) serial: String,
}

impl AdbShellRunner {
    pub fn new(serial: impl Into<String>) -> Self {
        Self { serial: serial.into() }
    }
}

#[async_trait]
impl ShellCommandRunner for AdbShellRunner {
    async fn exec_command(&self, command: &str) -> Result<String> {
        let output = Command::new("adb")
            .args(["-s", &self.serial, "shell", command])
            .output()
            .await
            .map_err(|e| CoreError::BridgeFailure {
                bridge: "ShellBridge",
                message: format!("Failed executing adb shell command: {}", e),
            })?;

        if !output.status.success() {
            let stderr = String::from_utf8_lossy(&output.stderr);
            return Err(CoreError::BridgeFailure {
                bridge: "ShellBridge",
                message: format!("shell command failed: {}", stderr.trim()),
            });
        }

        Ok(String::from_utf8_lossy(&output.stdout).to_string())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn runners_instantiate_with_serial() {
        let root = AdbRootRunner::new("127.0.0.1:5555");
        assert_eq!(root.serial, "127.0.0.1:5555");

        let shell = AdbShellRunner::new("127.0.0.1:5555");
        assert_eq!(shell.serial, "127.0.0.1:5555");
    }
}
