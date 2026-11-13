use async_trait::async_trait;
use tokio::process::Command;
use crate::error::Result;
use super::super::adapter::LockerAdapter;

pub struct DmsAdapter;

#[async_trait]
impl LockerAdapter for DmsAdapter {
    fn name(&self) -> &'static str {
        "dms"
    }

    async fn is_available(&self) -> bool {
        let which = Command::new("which").arg("dms").output().await;
        if !which.map(|o| o.status.success()).unwrap_or(false) {
            return false;
        }
        let pgrep = Command::new("pgrep").arg("-x").arg("dms").output().await;
        pgrep.map(|o| o.status.success()).unwrap_or(false)
    }

    async fn is_locked(&self) -> bool {
        let out = Command::new("dms")
            .args(["ipc", "call", "lock", "isLocked"])
            .output()
            .await;
        match out {
            Ok(o) => String::from_utf8_lossy(&o.stdout).trim() == "true",
            Err(_) => false,
        }
    }

    async fn lock(&self) -> Result<()> {
        let _ = Command::new("dms").args(["ipc", "call", "lock", "lock"]).status().await;
        if let Some(id) = super::logind::get_active_session_id().await {
            let _ = Command::new("loginctl").args(["lock-session", &id]).status().await;
        }
        Ok(())
    }

    async fn unlock(&self) -> Result<()> {
        let _ = Command::new("dms")
            .args(["ipc", "call", "lock", "unlock"])
            .status()
            .await;
        if let Some(id) = super::logind::get_active_session_id().await {
            let _ = Command::new("loginctl").args(["unlock-session", &id]).status().await;
        }
        Ok(())
    }

    async fn inhibit_idle(&self) -> Result<()> {
        let _ = Command::new("dms")
            .args(["ipc", "call", "inhibit", "enable"])
            .status()
            .await;
        Ok(())
    }

    async fn uninhibit_idle(&self) -> Result<()> {
        let _ = Command::new("dms")
            .args(["ipc", "call", "inhibit", "disable"])
            .status()
            .await;
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_dms_adapter_name() {
        let adapter = DmsAdapter;
        assert_eq!(adapter.name(), "dms");
    }
}

