use async_trait::async_trait;
use tokio::process::Command;
use crate::error::Result;
use super::super::adapter::LockerAdapter;

pub struct LogindAdapter;

pub async fn get_active_session_id() -> Option<String> {
    if let Ok(id) = std::env::var("XDG_SESSION_ID") {
        let trimmed = id.trim();
        if !trimmed.is_empty() { return Some(trimmed.to_string()); }
    }
    let uid = format!("{}", unsafe { libc::getuid() });
    if let Ok(out) = Command::new("loginctl").args(["show-user", &uid, "-p", "Display", "--value"]).output().await {
        let val = String::from_utf8_lossy(&out.stdout).trim().to_string();
        if !val.is_empty() && val != "0" { return Some(val); }
    }
    if let Ok(out) = Command::new("loginctl").args(["list-sessions", "--no-legend"]).output().await {
        for line in String::from_utf8_lossy(&out.stdout).lines() {
            let p: Vec<&str> = line.split_whitespace().collect();
            if p.len() >= 6 && p.contains(&"user") && p.iter().any(|s| s.starts_with("seat")) {
                return Some(p[0].to_string());
            }
        }
    }
    None
}

#[async_trait]
impl LockerAdapter for LogindAdapter {
    fn name(&self) -> &'static str { "logind" }
    async fn is_available(&self) -> bool { true }

    async fn is_locked(&self) -> bool {
        // 1. DMS IPC checks
        if let Ok(out) = Command::new("dms").args(["ipc", "call", "lock", "isLocked"]).output().await {
            if out.status.success() && String::from_utf8_lossy(&out.stdout).trim() == "true" { return true; }
        }
        if let Ok(out) = Command::new("dms").args(["ipc", "call", "lock", "status"]).output().await {
            let s = String::from_utf8_lossy(&out.stdout);
            if out.status.success() && ["sessionLockSecure\":true", "sessionLockLocked\":true", "shouldLock\":true", "loginctlLocked\":true"].iter().any(|k| s.contains(k)) {
                return true;
            }
        }

        // 2. Active screen locker binaries
        const LOCKERS: &[&str] = &["hyprlock", "swaylock", "waylock", "gtklock", "physlock", "slock", "i3lock", "kscreenlocker", "swaylock-effects", "swaylock-plugin"];
        for locker in LOCKERS {
            if Command::new("pgrep").arg("-x").arg(locker).output().await.map(|o| o.status.success()).unwrap_or(false) { return true; }
        }

        // 3. Hyprland layer surfaces
        if let Ok(out) = Command::new("hyprctl").arg("layers").output().await {
            let s = String::from_utf8_lossy(&out.stdout);
            const LAYERS: &[&str] = &["hyprlock", "lockscreen", "ext-session-lock-v1", "dms:fade-to-lock", "dms:fade-to-dpms", "dms:lock"];
            if LAYERS.iter().any(|l| s.contains(&format!("namespace: {l}"))) { return true; }
        }

        // 4. systemd-logind LockedHint
        if let Some(id) = get_active_session_id().await {
            if let Ok(out) = Command::new("loginctl").args(["show-session", &id, "-p", "LockedHint", "--value"]).output().await {
                if out.status.success() && String::from_utf8_lossy(&out.stdout).trim() == "yes" { return true; }
            }
        }
        if let Ok(o) = Command::new("busctl").args(["get-property", "org.freedesktop.login1", "/org/freedesktop/login1/session/auto", "org.freedesktop.login1.Session", "LockedHint"]).output().await {
            if o.status.success() && String::from_utf8_lossy(&o.stdout).contains("true") { return true; }
        }

        // 5. ScreenSaver D-Bus interface
        if let Ok(o) = Command::new("busctl").args(["--user", "call", "org.freedesktop.ScreenSaver", "/org/freedesktop/ScreenSaver", "org.freedesktop.ScreenSaver", "GetActive"]).output().await {
            if o.status.success() && String::from_utf8_lossy(&o.stdout).contains("true") { return true; }
        }

        false
    }

    async fn lock(&self) -> Result<()> {
        let _ = Command::new("dms").args(["ipc", "call", "lock", "lock"]).status().await;
        if let Some(id) = get_active_session_id().await {
            let _ = Command::new("loginctl").args(["lock-session", &id]).status().await;
        } else {
            let _ = Command::new("loginctl").arg("lock-session").status().await;
        }
        if Command::new("which").arg("hyprlock").output().await.map(|o| o.status.success()).unwrap_or(false) {
            let pgrep = Command::new("pgrep").arg("-x").arg("hyprlock").output().await.map(|o| o.status.success()).unwrap_or(false);
            if !pgrep { let _ = Command::new("hyprlock").spawn(); }
        }
        tokio::time::sleep(std::time::Duration::from_millis(80)).await;
        Ok(())
    }

    async fn unlock(&self) -> Result<()> {
        let _ = Command::new("dms").args(["ipc", "call", "lock", "unlock"]).status().await;
        if let Some(id) = get_active_session_id().await {
            let _ = Command::new("loginctl").args(["unlock-session", &id]).status().await;
        } else {
            let _ = Command::new("loginctl").arg("unlock-session").status().await;
        }
        let _ = Command::new("pkill").args(["-USR1", "hyprlock"]).status().await;
        let _ = Command::new("wtype").args(["-k", "Return"]).status().await;
        tokio::time::sleep(std::time::Duration::from_millis(80)).await;
        Ok(())
    }

    async fn inhibit_idle(&self) -> Result<()> {
        let _ = Command::new("dms").args(["ipc", "call", "inhibit", "enable"]).status().await;
        Ok(())
    }

    async fn uninhibit_idle(&self) -> Result<()> {
        let _ = Command::new("dms").args(["ipc", "call", "inhibit", "disable"]).status().await;
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn test_logind_adapter_basics() {
        let adapter = LogindAdapter;
        assert_eq!(adapter.name(), "logind");
        let _ = get_active_session_id().await;
    }
}
