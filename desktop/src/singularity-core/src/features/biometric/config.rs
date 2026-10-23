use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct BiometricConfig {
    pub enabled: bool,
    pub timeout_secs: u32,
    pub prompt_title: String,
}

impl Default for BiometricConfig {
    fn default() -> Self {
        Self {
            enabled: true,
            timeout_secs: 15,
            prompt_title: "Singularity Security".into(),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn default_biometric_config_is_enabled() {
        let config = BiometricConfig::default();
        assert!(config.enabled);
        assert_eq!(config.timeout_secs, 15);
        assert_eq!(config.prompt_title, "Singularity Security");
    }
}
