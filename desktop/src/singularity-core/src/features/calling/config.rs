use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CallingConfig {
    pub enabled: bool,
    pub auto_mute_pc_audio: bool,
}

impl Default for CallingConfig {
    fn default() -> Self {
        Self {
            enabled: true,
            auto_mute_pc_audio: true,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn default_calling_config_is_enabled() {
        let config = CallingConfig::default();
        assert!(config.enabled);
        assert!(config.auto_mute_pc_audio);
    }
}
