pub mod config;
pub mod handler;
pub mod state;

pub use self::config::CallingConfig;
pub use self::state::CallingState;

use async_trait::async_trait;
use crate::bridge::DeviceBridge;
use crate::error::Result;
use crate::event::DeviceEvent;
use crate::feature::{
    CommandResponse, Feature, FeatureCommand, FeatureRequirements, FeatureState,
    FeatureStatus, RequirementLevel,
};

pub struct CallingFeature {
    config: CallingConfig,
    state: CallingState,
}

impl CallingFeature {
    pub fn new(config: CallingConfig) -> Self {
        Self {
            config,
            state: CallingState::new(),
        }
    }
}

impl Default for CallingFeature {
    fn default() -> Self {
        Self::new(CallingConfig::default())
    }
}

#[async_trait]
impl Feature for CallingFeature {
    fn id(&self) -> &'static str {
        "calling"
    }

    fn name(&self) -> &'static str {
        "Desktop Calling"
    }

    fn requirements(&self) -> FeatureRequirements {
        FeatureRequirements {
            root: RequirementLevel::Optional,
            shell: RequirementLevel::Required,
            app: RequirementLevel::Optional,
        }
    }

    fn status(&self) -> FeatureStatus {
        FeatureStatus {
            id: self.id().into(),
            name: self.name().into(),
            state: if self.config.enabled {
                FeatureState::Active
            } else {
                FeatureState::Inactive
            },
        }
    }

    async fn on_connected(&mut self, _device: &DeviceBridge) -> Result<()> {
        self.state = CallingState::new();
        tracing::info!(target: "calling", "Calling feature connected");
        Ok(())
    }

    async fn on_disconnected(&mut self) -> Result<()> {
        self.state = CallingState::new();
        tracing::info!(target: "calling", "Calling feature reset on disconnect");
        Ok(())
    }

    async fn on_event(&mut self, event: &DeviceEvent, _device: &DeviceBridge) -> Result<()> {
        match event {
            DeviceEvent::IncomingCall { caller, .. } => {
                self.state.set_ringing(caller.clone());
            }
            DeviceEvent::CallStateChanged { state } => {
                if state == "IDLE" {
                    self.state.set_idle();
                } else if state == "OFFHOOK" {
                    self.state.set_active();
                }
            }
            _ => {}
        }
        Ok(())
    }

    async fn on_command(&mut self, cmd: &FeatureCommand, device: &DeviceBridge) -> Result<CommandResponse> {
        match cmd.action.as_str() {
            "accept_call" => {
                handler::handle_accept_call(&mut self.state, device).await?;
                Ok(CommandResponse::Ok(serde_json::json!({ "success": true })))
            }
            "end_call" => {
                handler::handle_end_call(&mut self.state, device).await?;
                Ok(CommandResponse::Ok(serde_json::json!({ "success": true })))
            }
            _ => Ok(CommandResponse::Unsupported),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::sync::Arc;
    use crate::bridge::{DeviceInfo, ShellBridge};

    #[tokio::test]
    async fn calling_feature_lifecycle_and_incoming_call() {
        let mut feature = CallingFeature::default();
        let info = DeviceInfo {
            serial: "test".into(),
            model: "test".into(),
            android_version: "16".into(),
        };
        let device = DeviceBridge::unrooted(info, Arc::new(ShellBridge::new()), None);

        let event = DeviceEvent::IncomingCall { caller: "Alice".into(), number: "123".into() };
        feature.on_event(&event, &device).await.unwrap();
        assert_eq!(feature.state.active_caller.as_deref(), Some("Alice"));

        let cmd = FeatureCommand {
            target_feature: "calling".into(),
            action: "accept_call".into(),
            payload: serde_json::Value::Null,
        };
        assert!(matches!(feature.on_command(&cmd, &device).await.unwrap(), CommandResponse::Ok(_)));
        assert!(feature.state.is_in_call);
    }
}
