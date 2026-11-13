use async_trait::async_trait;
use tokio::process::Command;
use crate::error::Result;
use super::super::adapter::LockerAdapter;

pub struct HyprlockAdapter;

#[async_trait]
impl LockerAdapter for HyprlockAdapter {
    fn name(&self) -> &'static str {
        "hyprlock"
    }

    async fn is_available(&self) -> bool {
        Command::new("which").arg("hyprlock").output().await.map(|o| o.status.success()).unwrap_or(false)
    }

    async fn is_locked(&self) -> bool {
        // 1. Process check
        if let Ok(pgrep) = Command::new("pgrep").arg("-x").arg("hyprlock").output().await {
            if pgrep.status.success() {
                return true;
            }
        }

        // 2. Layer surface namespace check
        if let Ok(out) = Command::new("hyprctl").arg("layers").output().await {
            let stdout = String::from_utf8_lossy(&out.stdout);
            for line in stdout.lines() {
                if line.contains("namespace: hyprlock")
                    || line.contains("namespace: lockscreen")
                    || line.contains("namespace: ext-session-lock-v1")
                {
                    return true;
                }
            }
        }
        false
    }

    async fn lock(&self) -> Result<()> {
        if let Some(id) = super::logind::get_active_session_id().await {
            let _ = Command::new("loginctl").args(["lock-session", &id]).status().await;
        } else {
            let _ = Command::new("loginctl").arg("lock-session").status().await;
        }
        Ok(())
    }

    async fn unlock(&self) -> Result<()> {
        let _ = Command::new("wtype").args(["-k", "Return"]).status().await;
        if let Some(id) = super::logind::get_active_session_id().await {
            let _ = Command::new("loginctl").args(["unlock-session", &id]).status().await;
        }
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_hyprlock_adapter_name() {
        let adapter = HyprlockAdapter;
        assert_eq!(adapter.name(), "hyprlock");
    }
}

