use std::path::PathBuf;
use clap::Parser;
use serde::{Deserialize, Serialize};

#[derive(Parser, Debug, Clone, Serialize, Deserialize)]
#[command(name = "singularity-daemon", about = "Singularity Convergence Daemon")]
pub struct DaemonConfig {
    /// Target Android device ADB TCP address (or "auto" for dynamic discovery)
    #[arg(long, default_value = "auto")]
    pub adb_address: String,

    /// Path to UNIX domain socket for IPC
    #[arg(long)]
    pub socket_path: Option<PathBuf>,

    /// Reconnection interval in seconds
    #[arg(long, default_value = "5")]
    pub reconnect_interval_secs: u64,

    /// Force daemon to simulate a non-rooted device (leaves phone root untouched)
    #[arg(long, env = "SINGULARITY_SIMULATE_NON_ROOT")]
    pub simulate_non_root: bool,
}

impl DaemonConfig {
    pub fn resolved_socket_path(&self) -> PathBuf {
        if let Some(ref path) = self.socket_path {
            return path.clone();
        }
        let uid = std::env::var("UID")
            .or_else(|_| std::env::var("USER").map(|_| "1000".into()))
            .unwrap_or_else(|_| "1000".into());
        PathBuf::from(format!("/run/user/{}/singularity.sock", uid))
    }
}

impl Default for DaemonConfig {
    fn default() -> Self {
        Self {
            adb_address: "auto".into(),
            socket_path: None,
            reconnect_interval_secs: 5,
            simulate_non_root: false,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn default_config_has_correct_target_and_root_simulation_disabled() {
        let config = DaemonConfig::default();
        assert_eq!(config.adb_address, "auto");
        assert_eq!(config.reconnect_interval_secs, 5);
        assert!(!config.simulate_non_root);
        assert!(config.resolved_socket_path().to_str().unwrap().contains("singularity.sock"));
    }

    #[test]
    fn simulate_non_root_flag_can_be_enabled() {
        let mut config = DaemonConfig::default();
        config.simulate_non_root = true;
        assert!(config.simulate_non_root);
    }
}
