pub mod config;
pub mod handler;
pub mod state;

pub use self::config::StorageConfig;
pub use self::state::StorageState;

use async_trait::async_trait;
use crate::bridge::DeviceBridge;
use crate::error::Result;
use crate::event::DeviceEvent;
use crate::feature::{
    CommandResponse, Feature, FeatureCommand, FeatureRequirements, FeatureState,
    FeatureStatus, RequirementLevel,
};

pub struct StorageFeature {
    config: StorageConfig,
    state: StorageState,
}

impl StorageFeature {
    pub fn new(config: StorageConfig) -> Self {
        Self { config, state: StorageState::new() }
    }
}

impl Default for StorageFeature {
    fn default() -> Self {
        Self::new(StorageConfig::default())
    }
}

#[async_trait]
impl Feature for StorageFeature {
    fn id(&self) -> &'static str {
        "storage"
    }

    fn name(&self) -> &'static str {
        "Transparent Storage"
    }

    fn requirements(&self) -> FeatureRequirements {
        FeatureRequirements {
            root: RequirementLevel::Required,
            shell: RequirementLevel::Required,
            app: RequirementLevel::Optional,
        }
    }

    fn status(&self) -> FeatureStatus {
        let state = if self.config.enabled { FeatureState::Active } else { FeatureState::Inactive };
        FeatureStatus { id: self.id().into(), name: self.name().into(), state }
    }

    async fn on_connected(&mut self, device: &DeviceBridge) -> Result<()> {
        let has_root = device.has_root();
        self.state.set_has_root(has_root);
        if !has_root {
            tracing::warn!(target: "storage_feature", "Device has no root bridge; storage feature degraded");
            return Ok(());
        }

        let (_, pub_key) = handler::ensure_host_ssh_key()?;
        handler::authorize_key_on_device(&pub_key, device).await?;
        handler::start_device_sshd(self.config.ssh_port, device).await?;
        if self.config.auto_mount {
            let mount_point = handler::resolve_mount_point(&self.config);
            let target_remote = config::default_remote_path(has_root).to_string();
            self.state.set_mounted(mount_point, target_remote);
        }
        Ok(())
    }

    async fn on_disconnected(&mut self) -> Result<()> {
        self.state.set_unmounted();
        self.state.set_has_root(false);
        tracing::info!(target: "storage_feature", "Storage feature reset on disconnect");
        Ok(())
    }

    async fn on_event(&mut self, event: &DeviceEvent, _device: &DeviceBridge) -> Result<()> {
        match event {
            DeviceEvent::StorageMounted { mount_point } => {
                self.state.set_mounted(mount_point.into(), self.config.remote_path.clone());
            }
            DeviceEvent::StorageUnmounted => {
                self.state.set_unmounted();
            }
            _ => {}
        }
        Ok(())
    }

    async fn on_command(&mut self, cmd: &FeatureCommand, device: &DeviceBridge) -> Result<CommandResponse> {
        match cmd.action.as_str() {
            "mount" => {
                let (_, pub_key) = handler::ensure_host_ssh_key()?;
                handler::authorize_key_on_device(&pub_key, device).await?;
                handler::start_device_sshd(self.config.ssh_port, device).await?;
                let mount_point = handler::resolve_mount_point(&self.config);
                self.state.set_mounted(mount_point.clone(), self.config.remote_path.clone());
                Ok(CommandResponse::Ok(serde_json::json!({
                    "mount_point": mount_point.to_string_lossy(),
                    "port": self.config.ssh_port
                })))
            }
            "unmount" => {
                handler::stop_device_sshd(device).await?;
                self.state.set_unmounted();
                Ok(CommandResponse::Ok(serde_json::json!({ "unmounted": true })))
            }
            _ => Ok(CommandResponse::Unsupported),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::sync::Arc;
    use crate::bridge::{DeviceInfo, RootBridge, ShellBridge};

    #[tokio::test]
    async fn storage_feature_lifecycle_and_status() {
        let mut feature = StorageFeature::default();
        assert_eq!(feature.id(), "storage");
        assert_eq!(feature.requirements().root, RequirementLevel::Required);

        let info = DeviceInfo {
            serial: "test".into(),
            model: "POCO F5".into(),
            android_version: "16".into(),
        };
        let device = DeviceBridge::new(info, Some(Arc::new(RootBridge::new())), Arc::new(ShellBridge::new()), None);

        assert!(feature.on_connected(&device).await.is_ok());
        let event = DeviceEvent::StorageMounted { mount_point: "/mnt/phone".into() };
        assert!(feature.on_event(&event, &device).await.is_ok());
        assert!(feature.state.is_mounted);

        assert!(feature.on_disconnected().await.is_ok());
        assert!(!feature.state.is_mounted);
    }
}
