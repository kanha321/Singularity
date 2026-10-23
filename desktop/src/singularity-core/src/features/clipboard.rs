pub mod config;
pub mod handler;
pub mod ring_buffer;
pub mod state;

pub use self::config::ClipboardConfig;
pub use self::handler::compute_hash;
pub use self::ring_buffer::EchoRingBuffer;
pub use self::state::ClipboardState;

use async_trait::async_trait;
use crate::bridge::DeviceBridge;
use crate::error::Result;
use crate::event::DeviceEvent;
use crate::feature::{
    CommandResponse, Feature, FeatureCommand, FeatureRequirements, FeatureState,
    FeatureStatus, RequirementLevel,
};

pub struct ClipboardFeature {
    config: ClipboardConfig,
    state: ClipboardState,
}

impl ClipboardFeature {
    pub fn new(config: ClipboardConfig) -> Self {
        Self {
            config,
            state: ClipboardState::new(),
        }
    }
}

impl Default for ClipboardFeature {
    fn default() -> Self {
        Self::new(ClipboardConfig::default())
    }
}

#[async_trait]
impl Feature for ClipboardFeature {
    fn id(&self) -> &'static str {
        "clipboard"
    }

    fn name(&self) -> &'static str {
        "Universal Clipboard"
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
        self.state = ClipboardState::new();
        tracing::info!(target: "clipboard", "Clipboard feature initialized");
        Ok(())
    }

    async fn on_disconnected(&mut self) -> Result<()> {
        self.state = ClipboardState::new();
        tracing::info!(target: "clipboard", "Clipboard feature reset on disconnect");
        Ok(())
    }

    async fn on_event(&mut self, event: &DeviceEvent, _device: &DeviceBridge) -> Result<()> {
        if let DeviceEvent::ClipboardChanged { text, sequence: _ } = event {
            let hash = handler::compute_hash(text);
            self.state.record_sync(hash);
            tracing::info!(target: "clipboard", len = text.len(), "Received clip from device");
        }
        Ok(())
    }

    async fn on_command(&mut self, cmd: &FeatureCommand, device: &DeviceBridge) -> Result<CommandResponse> {
        if cmd.action == "set_clipboard" {
            if let Some(text) = cmd.payload.as_str() {
                handler::handle_push_to_device(text, &self.config, &mut self.state, device).await?;
                return Ok(CommandResponse::Ok(serde_json::json!({ "success": true })));
            }
        }
        Ok(CommandResponse::Unsupported)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::sync::Arc;
    use crate::bridge::{DeviceInfo, ShellBridge};

    #[tokio::test]
    async fn clipboard_feature_status_and_lifecycle() {
        let mut feature = ClipboardFeature::default();
        assert_eq!(feature.id(), "clipboard");
        assert_eq!(feature.status().state, FeatureState::Active);

        let info = DeviceInfo {
            serial: "test".into(),
            model: "test".into(),
            android_version: "16".into(),
        };
        let device = DeviceBridge::unrooted(info, Arc::new(ShellBridge::new()), None);

        assert!(feature.on_connected(&device).await.is_ok());
        let event = DeviceEvent::ClipboardChanged { text: "hello".into(), sequence: 1 };
        assert!(feature.on_event(&event, &device).await.is_ok());
        assert_eq!(feature.state.clips_synced, 1);
        assert!(feature.on_disconnected().await.is_ok());
    }
}
