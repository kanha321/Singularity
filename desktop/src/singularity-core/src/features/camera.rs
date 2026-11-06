pub mod config;
pub mod handler;
pub mod state;

pub use self::config::FeatureCameraConfig;
pub use self::state::CameraState;

use async_trait::async_trait;
use crate::bridge::DeviceBridge;
use crate::error::Result;
use crate::event::DeviceEvent;
use crate::feature::{
    CommandResponse, Feature, FeatureCommand, FeatureRequirements, FeatureState,
    FeatureStatus, RequirementLevel,
};

pub struct CameraFeature {
    config: FeatureCameraConfig,
    state: CameraState,
}

impl CameraFeature {
    pub fn new(config: FeatureCameraConfig) -> Self {
        Self { config, state: CameraState::new() }
    }

    pub fn state(&self) -> &CameraState {
        &self.state
    }
}

impl Default for CameraFeature {
    fn default() -> Self {
        Self::new(FeatureCameraConfig::default())
    }
}

#[async_trait]
impl Feature for CameraFeature {
    fn id(&self) -> &'static str {
        "camera"
    }

    fn name(&self) -> &'static str {
        "Continuity Camera"
    }

    fn requirements(&self) -> FeatureRequirements {
        FeatureRequirements {
            root: RequirementLevel::Optional,
            shell: RequirementLevel::Required,
            app: RequirementLevel::Optional,
        }
    }

    fn status(&self) -> FeatureStatus {
        let state = if self.config.enabled && self.state.is_active() {
            FeatureState::Active
        } else {
            FeatureState::Inactive
        };
        FeatureStatus { id: self.id().into(), name: self.name().into(), state }
    }

    async fn on_connected(&mut self, _device: &DeviceBridge) -> Result<()> {
        Ok(())
    }

    async fn on_disconnected(&mut self) -> Result<()> {
        self.state.set_active(false);
        self.state.set_torch(false);
        Ok(())
    }

    async fn on_event(&mut self, _event: &DeviceEvent, _device: &DeviceBridge) -> Result<()> {
        Ok(())
    }

    async fn on_command(&mut self, cmd: &FeatureCommand, _device: &DeviceBridge) -> Result<CommandResponse> {
        let handler = handler::CameraCommandHandler::new(&self.state);
        handler.handle(cmd)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::sync::Arc;
    use crate::bridge::{DeviceInfo, ShellBridge};

    #[tokio::test]
    async fn feature_lifecycle() {
        let mut feature = CameraFeature::default();
        assert_eq!(feature.id(), "camera");
        assert_eq!(feature.status().state, FeatureState::Inactive);

        let info = DeviceInfo {
            serial: "test".into(),
            model: "test".into(),
            android_version: "16".into(),
        };
        let device = DeviceBridge::unrooted(info, Arc::new(ShellBridge::new()), None);

        let cmd = FeatureCommand {
            target_feature: "camera".into(),
            action: "start".into(),
            payload: serde_json::Value::Null,
        };
        let resp = feature.on_command(&cmd, &device).await.unwrap();
        assert!(matches!(resp, CommandResponse::Ok(_)));
        assert_eq!(feature.status().state, FeatureState::Active);

        feature.on_disconnected().await.unwrap();
        assert_eq!(feature.status().state, FeatureState::Inactive);
    }
}
