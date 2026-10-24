use std::sync::Arc;
use tokio::sync::RwLock;
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(default)]
pub struct CachedAuthSettings {
    pub login_mode: String,
    pub unlock_mode: String,
    pub sudo_mode: String,
    pub snooze_until: u64,
    pub notify_timeout_secs: u32,
    pub prompt_timeout_secs: u32,
    pub max_failed_attempts: u32,
}

impl Default for CachedAuthSettings {
    fn default() -> Self {
        Self {
            login_mode: "notify".into(),
            unlock_mode: "prompt".into(),
            sudo_mode: "prompt".into(),
            snooze_until: 0,
            notify_timeout_secs: 30,
            prompt_timeout_secs: 15,
            max_failed_attempts: 2,
        }
    }
}

impl CachedAuthSettings {
    pub fn is_snoozed(&self) -> bool {
        if self.snooze_until == 0 {
            return false;
        }
        let now = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .map(|d| d.as_millis() as u64)
            .unwrap_or(0);
        now < self.snooze_until
    }

    pub fn get_mode(&self, action: &str) -> &str {
        let act = action.to_lowercase();
        if act.contains("login") || act.contains("greetd") {
            &self.login_mode
        } else if act.contains("unlock") || act.contains("lock") || act.contains("screen") || act.contains("dms") {
            &self.unlock_mode
        } else {
            &self.sudo_mode
        }
    }

    pub fn is_denied(&self, action: &str) -> bool {
        self.get_mode(action).eq_ignore_ascii_case("deny")
    }

    pub fn get_effective_timeout(&self, action: &str) -> u32 {
        if self.get_mode(action).eq_ignore_ascii_case("notify") {
            self.notify_timeout_secs
        } else {
            self.prompt_timeout_secs
        }
    }
}

pub fn get_action_type(reason: &str) -> &'static str {
    let act = reason.to_lowercase();
    if act.contains("login") || act.contains("greetd") {
        "login"
    } else if act.contains("unlock") || act.contains("lock") || act.contains("screen") || act.contains("dms") {
        "unlock"
    } else {
        "sudo"
    }
}

pub type SharedAuthCache = Arc<RwLock<CachedAuthSettings>>;

pub fn new_auth_cache() -> SharedAuthCache {
    Arc::new(RwLock::new(CachedAuthSettings::default()))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn default_settings_allow_prompt_for_sudo_and_unlock() {
        let settings = CachedAuthSettings::default();
        assert_eq!(settings.get_mode("sudo"), "prompt");
        assert_eq!(settings.get_mode("unlock"), "prompt");
        assert_eq!(settings.get_mode("login"), "notify");
        assert!(!settings.is_denied("sudo"));
        assert!(!settings.is_snoozed());
    }

    #[test]
    fn snooze_detects_future_timestamp() {
        let mut settings = CachedAuthSettings::default();
        settings.snooze_until = u64::MAX;
        assert!(settings.is_snoozed());
    }
}
