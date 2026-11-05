use serde::{Deserialize, Serialize};
use std::path::PathBuf;

fn default_ssh_port() -> u16 {
    2222
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct StorageConfig {
    pub enabled: bool,
    pub mount_point: PathBuf,
    pub remote_path: String,
    #[serde(default = "default_ssh_port")]
    pub ssh_port: u16,
    pub auto_mount: bool,
    #[serde(default)]
    pub mount_root: bool,
}

impl Default for StorageConfig {
    fn default() -> Self {
        Self {
            enabled: true,
            mount_point: PathBuf::from("/mnt/phone"),
            remote_path: "/sdcard".into(),
            ssh_port: 2222,
            auto_mount: false,
            mount_root: false,
        }
    }
}

pub fn default_remote_path(has_root: bool) -> &'static str {
    if has_root {
        "/"
    } else {
        "/sdcard"
    }
}

pub fn canonical_remote_path(path: &str) -> String {
    let trimmed = path.trim();
    if trimmed.is_empty() || trimmed == "/sdcard" || trimmed == "/storage/emulated/0" {
        "/sdcard".into()
    } else {
        trimmed.into()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn default_config_has_phone_mount_and_sdcard_path() {
        let config = StorageConfig::default();
        assert!(config.enabled);
        assert_eq!(config.remote_path, "/sdcard");
        assert_eq!(config.ssh_port, 2222);
        assert!(!config.auto_mount);
        assert!(!config.mount_root);
        assert_eq!(config.mount_point, PathBuf::from("/mnt/phone"));
    }

    #[test]
    fn default_remote_path_selects_root_or_sdcard() {
        assert_eq!(default_remote_path(true), "/");
        assert_eq!(default_remote_path(false), "/sdcard");
    }

    #[test]
    fn canonical_remote_path_maps_sdcard_consistently() {
        assert_eq!(canonical_remote_path("/sdcard"), "/sdcard");
        assert_eq!(canonical_remote_path("/storage/emulated/0"), "/sdcard");
        assert_eq!(canonical_remote_path("/"), "/");
        assert_eq!(canonical_remote_path("/system"), "/system");
    }
}
