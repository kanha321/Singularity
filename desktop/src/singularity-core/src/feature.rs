use async_trait::async_trait;
pub use singularity_protocol::{
    CommandResponse, FeatureCommand, FeatureRequirements, FeatureState, FeatureStatus,
    RequirementLevel,
};
use crate::bridge::DeviceBridge;
use crate::error::Result;
use crate::event::DeviceEvent;

/// The unified trait that every Singularity feature implements.
#[async_trait]
pub trait Feature: Send + Sync {
    fn id(&self) -> &'static str;
    fn name(&self) -> &'static str;

    fn requirements(&self) -> FeatureRequirements {
        FeatureRequirements {
            root: RequirementLevel::Optional,
            shell: RequirementLevel::Required,
            app: RequirementLevel::Optional,
        }
    }

    fn status(&self) -> FeatureStatus;

    async fn on_connected(&mut self, device: &DeviceBridge) -> Result<()>;
    async fn on_disconnected(&mut self) -> Result<()>;
    async fn on_event(&mut self, event: &DeviceEvent, device: &DeviceBridge) -> Result<()>;

    async fn on_command(&mut self, _cmd: &FeatureCommand, _device: &DeviceBridge) -> Result<CommandResponse> {
        Ok(CommandResponse::Unsupported)
    }
}

/// Registry managing all active features and isolating event failures.
pub struct FeatureRegistry {
    features: Vec<Box<dyn Feature>>,
}

impl FeatureRegistry {
    pub fn new() -> Self {
        Self { features: Vec::new() }
    }

    pub fn register(&mut self, feature: Box<dyn Feature>) {
        self.features.push(feature);
    }

    pub fn statuses(&self) -> Vec<FeatureStatus> {
        self.features.iter().map(|f| f.status()).collect()
    }

    pub async fn on_connected(&mut self, device: &DeviceBridge) {
        for feature in &mut self.features {
            if let Err(e) = feature.on_connected(device).await {
                tracing::warn!(feature = feature.id(), error = %e, "Feature on_connected failed");
            }
        }
    }

    pub async fn on_disconnected(&mut self) {
        for feature in &mut self.features {
            if let Err(e) = feature.on_disconnected().await {
                tracing::warn!(feature = feature.id(), error = %e, "Feature on_disconnected failed");
            }
        }
    }

    pub async fn dispatch_event(&mut self, event: &DeviceEvent, device: &DeviceBridge) {
        for feature in &mut self.features {
            if let Err(e) = feature.on_event(event, device).await {
                tracing::warn!(feature = feature.id(), error = %e, "Feature on_event failed");
            }
        }
    }
}

impl Default for FeatureRegistry {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::sync::atomic::{AtomicUsize, Ordering};
    use std::sync::Arc;
    use crate::bridge::{DeviceInfo, ShellBridge};

    struct TestFeature {
        counter: Arc<AtomicUsize>,
    }

    #[async_trait]
    impl Feature for TestFeature {
        fn id(&self) -> &'static str { "test_feature" }
        fn name(&self) -> &'static str { "Test Feature" }
        fn status(&self) -> FeatureStatus {
            FeatureStatus {
                id: self.id().into(),
                name: self.name().into(),
                state: FeatureState::Active,
            }
        }
        async fn on_connected(&mut self, _dev: &DeviceBridge) -> Result<()> { Ok(()) }
        async fn on_disconnected(&mut self) -> Result<()> { Ok(()) }
        async fn on_event(&mut self, _ev: &DeviceEvent, _dev: &DeviceBridge) -> Result<()> {
            self.counter.fetch_add(1, Ordering::SeqCst);
            Ok(())
        }
    }

    #[tokio::test]
    async fn registry_dispatches_events_to_registered_features() {
        let counter = Arc::new(AtomicUsize::new(0));
        let mut registry = FeatureRegistry::new();
        registry.register(Box::new(TestFeature { counter: counter.clone() }));

        let info = DeviceInfo {
            serial: "test".into(),
            model: "test".into(),
            android_version: "16".into(),
        };
        let device = DeviceBridge::new(info, None, Arc::new(ShellBridge::new()), None);

        let event = DeviceEvent::BatteryUpdate { percentage: 50, temperature: 25.0, is_charging: false };
        registry.dispatch_event(&event, &device).await;

        assert_eq!(counter.load(Ordering::SeqCst), 1);
        assert_eq!(registry.statuses().len(), 1);
    }
}
