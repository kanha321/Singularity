use std::collections::HashMap;
use std::sync::Arc;
use tokio::sync::mpsc::{channel, Receiver};
use tokio::sync::{Mutex, OnceCell};
use singularity_protocol::{DesktopNotification, NotificationActionInvoke, NotificationDismiss, PhoneNotification};
use super::dbus_monitor::{DbusNotificationMonitor, MonitorEvent};
use super::dbus_sender::DbusNotificationSender;

static GLOBAL_HUB: OnceCell<Arc<NotificationHub>> = OnceCell::const_new();

pub struct NotificationHub {
    sender: DbusNotificationSender,
    phone_to_desktop: Mutex<HashMap<String, u32>>,
    desktop_to_phone: Mutex<HashMap<u32, String>>,
    active_notifications: Mutex<HashMap<String, PhoneNotification>>,
    pending_for_phone: Mutex<Vec<DesktopNotification>>,
    pending_dismiss_for_phone: Mutex<Vec<NotificationDismiss>>,
    pending_actions_for_phone: Mutex<Vec<NotificationActionInvoke>>,
    monitor_rx: Mutex<Receiver<MonitorEvent>>,
}

impl NotificationHub {
    pub async fn global() -> Arc<Self> { GLOBAL_HUB.get_or_init(Self::start).await.clone() }
    pub fn get() -> Option<Arc<Self>> { GLOBAL_HUB.get().cloned() }

    pub async fn start() -> Arc<Self> {
        let (tx, rx) = channel(64);
        let _handle = DbusNotificationMonitor::start(tx);
        Arc::new(Self {
            sender: DbusNotificationSender::new().await,
            phone_to_desktop: Mutex::new(HashMap::new()), desktop_to_phone: Mutex::new(HashMap::new()),
            active_notifications: Mutex::new(HashMap::new()), pending_for_phone: Mutex::new(Vec::new()),
            pending_dismiss_for_phone: Mutex::new(Vec::new()), pending_actions_for_phone: Mutex::new(Vec::new()),
            monitor_rx: Mutex::new(rx),
        })
    }

    pub async fn post_phone_notification(&self, notif: PhoneNotification, is_locked: bool) {
        let replaces_id = self.phone_to_desktop.lock().await.get(&notif.id).copied().unwrap_or(0);
        let new_id = self.sender.post_notification(&notif, replaces_id, is_locked).await;
        if new_id > 0 {
            self.phone_to_desktop.lock().await.insert(notif.id.clone(), new_id);
            self.desktop_to_phone.lock().await.insert(new_id, notif.id.clone());
            self.active_notifications.lock().await.insert(notif.id.clone(), notif);
        }
    }

    pub async fn dismiss_from_phone(&self, phone_id: &str) {
        self.active_notifications.lock().await.remove(phone_id);
        if let Some(id) = self.phone_to_desktop.lock().await.remove(phone_id) {
            self.desktop_to_phone.lock().await.remove(&id);
            self.sender.close_notification(id).await;
        } else if let Ok(id) = phone_id.parse::<u32>() { self.sender.close_notification(id).await; }
    }

    pub async fn get_active_notifications(&self) -> Vec<PhoneNotification> {
        self.active_notifications.lock().await.values().cloned().collect()
    }

    pub async fn send_reply(&self, target_id: &str, text: &str) {
        let id = self.resolve_phone_id(target_id).await;
        self.pending_actions_for_phone.lock().await.push(NotificationActionInvoke { id, action_id: "reply".into(), reply_text: Some(text.into()) });
    }

    pub async fn invoke_action(&self, target_id: &str, action_id: &str) {
        let id = self.resolve_phone_id(target_id).await;
        self.pending_actions_for_phone.lock().await.push(NotificationActionInvoke { id, action_id: action_id.into(), reply_text: None });
    }

    async fn resolve_phone_id(&self, target: &str) -> String {
        if let Ok(d_id) = target.parse::<u32>() {
            if let Some(p_id) = self.desktop_to_phone.lock().await.get(&d_id) { return p_id.clone(); }
        }
        let map = self.active_notifications.lock().await;
        if map.contains_key(target) { return target.to_string(); }
        let low = target.to_lowercase();
        map.iter().find(|(_, n)| n.app_name.to_lowercase().contains(&low) || n.title.to_lowercase().contains(&low))
            .map(|(id, _)| id.clone()).unwrap_or_else(|| target.to_string())
    }

    pub async fn pop_events_for_phone(&self) -> (Vec<DesktopNotification>, Vec<NotificationDismiss>, Vec<NotificationActionInvoke>) {
        let mut rx = self.monitor_rx.lock().await;
        while let Ok(event) = rx.try_recv() {
            match event {
                MonitorEvent::Posted(notif) => self.pending_for_phone.lock().await.push(notif),
                MonitorEvent::Closed { desktop_id, reason } => self.handle_closed(desktop_id, reason).await,
                MonitorEvent::Replied { desktop_id, text } => {
                    if let Some(p_id) = self.desktop_to_phone.lock().await.get(&desktop_id) {
                        self.pending_actions_for_phone.lock().await.push(NotificationActionInvoke { id: p_id.clone(), action_id: "reply".into(), reply_text: Some(text) });
                    }
                }
                MonitorEvent::ActionInvoked { desktop_id, action_id } => {
                    if let Some(p_id) = self.desktop_to_phone.lock().await.get(&desktop_id) {
                        if action_id == "reply" {
                            let label = self.active_notifications.lock().await.get(p_id).map(|n| format!("{} ({})", n.app_name, n.title)).unwrap_or_else(|| "Phone Notification".into());
                            super::prompt::spawn_reply_prompt(label, p_id.clone());
                        } else {
                            self.pending_actions_for_phone.lock().await.push(NotificationActionInvoke { id: p_id.clone(), action_id, reply_text: None });
                        }
                    }
                }
            }
        }
        (
            std::mem::take(&mut *self.pending_for_phone.lock().await),
            std::mem::take(&mut *self.pending_dismiss_for_phone.lock().await),
            std::mem::take(&mut *self.pending_actions_for_phone.lock().await),
        )
    }

    async fn handle_closed(&self, desktop_id: u32, reason: u32) {
        if reason == 2 {
            if let Some(target_id) = self.desktop_to_phone.lock().await.remove(&desktop_id) {
                self.phone_to_desktop.lock().await.remove(&target_id);
                if let Some(n) = self.active_notifications.lock().await.remove(&target_id) {
                    if n.is_clearable { self.pending_dismiss_for_phone.lock().await.push(NotificationDismiss { id: target_id, source: "desktop".into() }); }
                }
            } else {
                self.pending_dismiss_for_phone.lock().await.push(NotificationDismiss { id: desktop_id.to_string(), source: "desktop".into() });
            }
        } else if reason == 3 {
            if let Some(target_id) = self.desktop_to_phone.lock().await.remove(&desktop_id) {
                self.phone_to_desktop.lock().await.remove(&target_id);
                self.active_notifications.lock().await.remove(&target_id);
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn test_hub_lifecycle() {
        let hub = NotificationHub::start().await;
        let p_notif = PhoneNotification {
            id: "msg_123".into(), package_name: "com.whatsapp".into(), app_name: "WhatsApp".into(),
            title: "Bob".into(), text: "Hello".into(), timestamp: 100, is_clearable: true,
            can_reply: true, actions: vec![], icon_base64: None, group_key: None,
        };
        hub.post_phone_notification(p_notif, false).await;
        assert_eq!(hub.resolve_phone_id("whatsapp").await, "msg_123");
        hub.send_reply("msg_123", "Hey!").await;
        let (_, _, invokes) = hub.pop_events_for_phone().await;
        assert_eq!(invokes.len(), 1);
        hub.dismiss_from_phone("msg_123").await;
    }
}
