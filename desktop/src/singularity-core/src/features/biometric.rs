pub mod config;
pub mod handler;
pub mod state;

pub use self::config::BiometricConfig;
pub use self::state::BiometricState;

use async_trait::async_trait;
use crate::bridge::DeviceBridge;
use crate::error::Result;
use crate::event::DeviceEvent;
use crate::feature::{
    CommandResponse, Feature, FeatureCommand, FeatureRequirements, FeatureState,
    FeatureStatus, RequirementLevel,
};

pub struct BiometricFeature {
    config: BiometricConfig,
    state: BiometricState,
}

impl BiometricFeature {
    pub fn new(config: BiometricConfig) -> Self {
        Self { config, state: BiometricState::new() }
    }
}

impl Default for BiometricFeature {
    fn default() -> Self {
        Self::new(BiometricConfig::default())
    }
}

#[async_trait]
impl Feature for BiometricFeature {
    fn id(&self) -> &'static str { "biometric" }
    fn name(&self) -> &'static str { "Proximity Biometrics" }

    fn requirements(&self) -> FeatureRequirements {
        FeatureRequirements {
            root: RequirementLevel::Optional,
            shell: RequirementLevel::Required,
            app: RequirementLevel::Required,
        }
    }

    fn status(&self) -> FeatureStatus {
        let state = if !self.config.enabled {
            FeatureState::Inactive
        } else if self.state.is_available {
            FeatureState::Active
        } else {
            FeatureState::Degraded { reason: "Awaiting AppBridge connection".into() }
        };
        FeatureStatus { id: self.id().into(), name: self.name().into(), state }
    }

    async fn on_connected(&mut self, device: &DeviceBridge) -> Result<()> {
        let available = device.has_app() || device.has_root();
        self.state.set_available(available);
        if !available {
            tracing::warn!(target: "biometric_feature", "AppBridge unavailable");
        } else {
            tracing::info!(target: "biometric_feature", has_app = device.has_app(), "Biometric feature ready");
        }
        Ok(())
    }

    async fn on_disconnected(&mut self) -> Result<()> {
        self.state.reset();
        tracing::info!(target: "biometric_feature", "Biometric feature reset on disconnect");
        Ok(())
    }

    async fn on_event(&mut self, _event: &DeviceEvent, _device: &DeviceBridge) -> Result<()> {
        Ok(())
    }

    async fn on_command(&mut self, cmd: &FeatureCommand, device: &DeviceBridge) -> Result<CommandResponse> {
        match cmd.action.as_str() {
            "authenticate" => {
                let reason = cmd.payload.get("reason").and_then(|v| v.as_str())
                    .unwrap_or("Singularity Security Verification");
                let timeout = cmd.payload.get("timeout_secs").and_then(|v| v.as_u64())
                    .map(|v| v as u32).unwrap_or(self.config.timeout_secs);

                let success = handler::handle_authenticate(
                    &self.config.prompt_title, reason, timeout, &mut self.state, device,
                ).await?;

                Ok(CommandResponse::Ok(serde_json::json!({
                    "success": success,
                    "attempts": self.state.total_auth_count,
                })))
            }
            _ => Ok(CommandResponse::Unsupported),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::sync::Arc;
    use crate::bridge::{AppBridge, DeviceInfo, ShellBridge};

    #[tokio::test]
    async fn biometric_feature_lifecycle_and_status() {
        let mut feature = BiometricFeature::default();
        assert_eq!(feature.id(), "biometric");
        assert!(matches!(feature.status().state, FeatureState::Degraded { .. }));

        let info = DeviceInfo { serial: "test".into(), model: "test".into(), android_version: "16".into() };
        let device = DeviceBridge::unrooted(info, Arc::new(ShellBridge::new()), Some(Arc::new(AppBridge::new())));

        feature.on_connected(&device).await.unwrap();
        assert!(matches!(feature.status().state, FeatureState::Active));

        let cmd = FeatureCommand {
            target_feature: "biometric".into(),
            action: "authenticate".into(),
            payload: serde_json::json!({ "reason": "Test", "timeout_secs": 5 }),
        };
        let res = feature.on_command(&cmd, &device).await.unwrap();
        assert!(matches!(res, CommandResponse::Ok(_)));

        feature.on_disconnected().await.unwrap();
        assert!(matches!(feature.status().state, FeatureState::Degraded { .. }));
    }
}
