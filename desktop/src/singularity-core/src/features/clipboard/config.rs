use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ClipboardConfig {
    pub enabled: bool,
    pub max_size_bytes: usize,
}

impl Default for ClipboardConfig {
    fn default() -> Self {
        Self {
            enabled: true,
            max_size_bytes: 1024 * 1024, // 1MB maximum clip size
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn default_clipboard_config_is_enabled() {
        let config = ClipboardConfig::default();
        assert!(config.enabled);
        assert_eq!(config.max_size_bytes, 1024 * 1024);
    }
}
