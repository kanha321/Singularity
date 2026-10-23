use std::sync::Arc;
use tokio::sync::broadcast;
use crate::bridge::{AppBridge, DeviceInfo, RootBridge, ShellBridge};
use crate::error::{CoreError, Result};
use crate::event::DeviceEvent;

/// Composite parent bridge representing a target Android device.
pub struct DeviceBridge {
    pub info: DeviceInfo,
    root: Option<Arc<RootBridge>>,
    shell: Arc<ShellBridge>,
    app: Option<Arc<AppBridge>>,
    event_tx: broadcast::Sender<DeviceEvent>,
}

impl DeviceBridge {
    pub fn new(
        info: DeviceInfo,
        root: Option<Arc<RootBridge>>,
        shell: Arc<ShellBridge>,
        app: Option<Arc<AppBridge>>,
    ) -> Self {
        let (event_tx, _) = broadcast::channel(128);
        Self {
            info,
            root,
            shell,
            app,
            event_tx,
        }
    }

    /// Creates a DeviceBridge explicitly simulating an unrooted device (root: None)
    pub fn unrooted(
        info: DeviceInfo,
        shell: Arc<ShellBridge>,
        app: Option<Arc<AppBridge>>,
    ) -> Self {
        Self::new(info, None, shell, app)
    }

    pub fn has_root(&self) -> bool {
        self.root.is_some()
    }

    pub fn has_app(&self) -> bool {
        self.app.is_some()
    }

    pub fn root(&self) -> Result<&Arc<RootBridge>> {
        self.root
            .as_ref()
            .ok_or(CoreError::BridgeUnavailable("RootBridge"))
    }

    pub fn shell(&self) -> &Arc<ShellBridge> {
        &self.shell
    }

    pub fn app(&self) -> Result<&Arc<AppBridge>> {
        self.app
            .as_ref()
            .ok_or(CoreError::BridgeUnavailable("AppBridge"))
    }

    pub fn broadcast(&self, event: DeviceEvent) -> Result<usize> {
        self.event_tx
            .send(event)
            .map_err(|e| CoreError::BroadcastError(e.to_string()))
    }

    pub fn subscribe(&self) -> broadcast::Receiver<DeviceEvent> {
        self.event_tx.subscribe()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn device_bridge_broadcasts_events_to_subscribers() {
        let info = DeviceInfo {
            serial: "10.235.203.184:5555".into(),
            model: "POCO F5".into(),
            android_version: "16".into(),
        };
        let device = DeviceBridge::new(
            info,
            Some(Arc::new(RootBridge::new())),
            Arc::new(ShellBridge::new()),
            None,
        );

        assert!(device.has_root());
        assert!(!device.has_app());
        assert!(device.root().is_ok());
        assert!(device.app().is_err());

        let mut rx = device.subscribe();
        let event = DeviceEvent::BatteryUpdate {
            percentage: 80,
            temperature: 30.5,
            is_charging: true,
        };

        device.broadcast(event.clone()).unwrap();
        let received = rx.recv().await.unwrap();
        assert_eq!(event, received);
    }

    #[test]
    fn device_bridge_unrooted_constructor_disables_root() {
        let info = DeviceInfo {
            serial: "10.235.203.184:5555".into(),
            model: "POCO F5".into(),
            android_version: "16".into(),
        };
        let device = DeviceBridge::unrooted(info, Arc::new(ShellBridge::new()), None);
        assert!(!device.has_root());
        assert!(device.root().is_err());
    }
}
