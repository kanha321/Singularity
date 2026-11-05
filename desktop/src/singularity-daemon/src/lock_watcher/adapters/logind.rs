use async_trait::async_trait;
use tokio::process::Command;
use crate::error::Result;
use super::super::adapter::LockerAdapter;

pub struct LogindAdapter;

#[async_trait]
impl LockerAdapter for LogindAdapter {
    fn name(&self) -> &'static str {
        "logind"
    }

    async fn is_available(&self) -> bool {
        Command::new("which").arg("loginctl").output().await.map(|o| o.status.success()).unwrap_or(false)
    }

    async fn is_locked(&self) -> bool {
        // 1. Try D-Bus ScreenSaver GetActive
        let bus = Command::new("busctl")
            .args([
                "--user", "call",
                "org.freedesktop.ScreenSaver",
                "/org/freedesktop/ScreenSaver",
                "org.freedesktop.ScreenSaver",
                "GetActive",
            ])
            .output()
            .await;
        if let Ok(o) = bus {
            if o.status.success() && String::from_utf8_lossy(&o.stdout).contains("true") {
                return true;
            }
        }

        // 2. Try loginctl LockedHint
        if let Ok(out) = Command::new("loginctl").args(["show-session", "-p", "LockedHint"]).output().await {
            if String::from_utf8_lossy(&out.stdout).contains("LockedHint=yes") {
                return true;
            }
        }

        false
    }

    async fn lock(&self) -> Result<()> {
        let _ = Command::new("loginctl").args(["lock-session"]).status().await;
        Ok(())
    }

    async fn unlock(&self) -> Result<()> {
        let _ = Command::new("loginctl").args(["unlock-session"]).status().await;
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_logind_adapter_name() {
        let adapter = LogindAdapter;
        assert_eq!(adapter.name(), "logind");
    }
}

