use async_trait::async_trait;
use tokio::process::Command;
use crate::error::Result;
use super::super::adapter::LockerAdapter;

pub struct CaelestiaAdapter;

#[async_trait]
impl LockerAdapter for CaelestiaAdapter {
    fn name(&self) -> &'static str {
        "caelestia"
    }

    async fn is_available(&self) -> bool {
        let which = Command::new("which").arg("caelestia").output().await;
        if !which.map(|o| o.status.success()).unwrap_or(false) {
            return false;
        }
        let pgrep = Command::new("pgrep").args(["-f", "caelestia shell|qs.*caelestia"]).output().await;
        pgrep.map(|o| o.status.success()).unwrap_or(false)
    }

    async fn is_locked(&self) -> bool {
        let out = Command::new("caelestia")
            .args(["shell", "lock", "isLocked"])
            .output()
            .await;
        match out {
            Ok(o) => String::from_utf8_lossy(&o.stdout).trim() == "true",
            Err(_) => false,
        }
    }

    async fn lock(&self) -> Result<()> {
        let _ = Command::new("caelestia")
            .args(["shell", "lock", "lock"])
            .status()
            .await;
        Ok(())
    }

    async fn unlock(&self) -> Result<()> {
        let _ = Command::new("caelestia")
            .args(["shell", "lock", "unlock"])
            .status()
            .await;
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_caelestia_adapter_name() {
        let adapter = CaelestiaAdapter;
        assert_eq!(adapter.name(), "caelestia");
    }
}

