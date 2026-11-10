use futures::StreamExt;
use std::collections::HashMap;
use tokio::sync::mpsc::Sender;
use zbus::message::Type;
use zbus::zvariant::OwnedValue;
use singularity_protocol::DesktopNotification;

#[derive(Debug, Clone)]
pub enum MonitorEvent {
    Posted(DesktopNotification),
    Closed { desktop_id: u32, reason: u32 },
    ActionInvoked { desktop_id: u32, action_id: String },
    Replied { desktop_id: u32, text: String },
}

pub struct DbusNotificationMonitor;

impl DbusNotificationMonitor {
    pub fn start(tx: Sender<MonitorEvent>) -> tokio::task::JoinHandle<()> {
        tokio::spawn(async move {
            tracing::info!(target: "dbus_monitor", "Starting D-Bus desktop notification monitor");
            if let Err(e) = Self::run_monitor(tx).await {
                tracing::warn!(target: "dbus_monitor", "D-Bus monitor ended: {}", e);
            }
        })
    }

    async fn run_monitor(tx: Sender<MonitorEvent>) -> std::result::Result<(), Box<dyn std::error::Error + Send + Sync>> {
        let conn = zbus::connection::Builder::session()?.build().await?;
        let rules = vec![
            "type='method_call',interface='org.freedesktop.Notifications',member='Notify'",
            "type='method_return'",
            "type='signal',interface='org.freedesktop.Notifications',member='NotificationClosed'",
            "type='signal',interface='org.freedesktop.Notifications',member='ActionInvoked'",
            "type='signal',interface='org.freedesktop.Notifications',member='NotificationReplied'",
        ];

        let proxy = zbus::Proxy::new(&conn, "org.freedesktop.DBus", "/org/freedesktop/DBus", "org.freedesktop.DBus.Monitoring").await?;
        let _: () = proxy.call("BecomeMonitor", &(rules, 0u32)).await?;
        tracing::info!(target: "dbus_monitor", "D-Bus notification monitor active with BecomeMonitor");
        let mut stream = zbus::MessageStream::from(&conn);
        let mut pending_calls: HashMap<u32, DesktopNotification> = HashMap::new();

        while let Some(msg_res) = stream.next().await {
            let msg = match msg_res { Ok(m) => m, Err(_) => continue };
            let hdr = msg.header();
            let member = hdr.member().map(|m| m.as_str()).unwrap_or("");

            match msg.message_type() {
                Type::MethodCall if member == "Notify" => {
                    type NotifyArgs = (String, u32, String, String, String, Vec<String>, HashMap<String, OwnedValue>, i32);
                    if let Ok(body) = msg.body().deserialize::<NotifyArgs>() {
                        let (app_name, replaces_id, _icon, summary, text, _actions, hints, timeout) = body;
                        if Self::is_self_notification(&app_name, &hints) { continue; }
                        tracing::info!(target: "dbus_monitor", app = %app_name, summary = %summary, "Captured desktop notification to sync to phone");
                        let notif = DesktopNotification {
                            id: replaces_id,
                            app_name, summary, body: text, urgency: 1,
                            timeout_ms: if timeout > 0 { timeout } else { 5000 },
                        };
                        let s = msg.primary_header().serial_num();
                        if pending_calls.len() > 100 { pending_calls.clear(); }
                        pending_calls.insert(s.into(), notif);
                    }
                }
                Type::MethodReturn => {
                    if let Some(reply_s) = hdr.reply_serial() {
                        if let Some(mut notif) = pending_calls.remove(&reply_s.get()) {
                            if let Ok(assigned_id) = msg.body().deserialize::<u32>() {
                                notif.id = assigned_id;
                                tracing::info!(target: "dbus_monitor", id = assigned_id, "Notification ID assigned from desktop server");
                                let _ = tx.send(MonitorEvent::Posted(notif)).await;
                            }
                        }
                    }
                }
                Type::Signal if member == "NotificationClosed" => {
                    match msg.body().deserialize::<(u32, u32)>() {
                        Ok((id, reason)) => {
                            tracing::info!(target: "dbus_monitor", id = id, reason = reason, "Captured NotificationClosed signal from desktop");
                            let _ = tx.send(MonitorEvent::Closed { desktop_id: id, reason }).await;
                        }
                        Err(e) => tracing::warn!(target: "dbus_monitor", error = %e, "Failed to deserialize NotificationClosed body"),
                    }
                }
                Type::Signal if member == "ActionInvoked" => {
                    if let Ok((id, action_key)) = msg.body().deserialize::<(u32, String)>() {
                        tracing::info!(target: "dbus_monitor", id = id, action = %action_key, "Captured ActionInvoked signal from desktop");
                        let _ = tx.send(MonitorEvent::ActionInvoked { desktop_id: id, action_id: action_key }).await;
                    }
                }
                Type::Signal if member == "NotificationReplied" => {
                    if let Ok((id, text)) = msg.body().deserialize::<(u32, String)>() {
                        tracing::info!(target: "dbus_monitor", id = id, "Captured NotificationReplied signal from desktop");
                        let _ = tx.send(MonitorEvent::Replied { desktop_id: id, text }).await;
                    }
                }
                _ => {}
            }
        }
        Ok(())
    }

    pub fn is_self_notification(app_name: &str, hints: &HashMap<String, OwnedValue>) -> bool {
        app_name.contains("(Phone)") || app_name.starts_with("Singularity") || hints.contains_key("singularity-origin")
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_echo_prevention_logic() {
        let mut hints = HashMap::new();
        assert!(!DbusNotificationMonitor::is_self_notification("Slack", &hints));
        assert!(!DbusNotificationMonitor::is_self_notification("Firefox", &hints));

        assert!(DbusNotificationMonitor::is_self_notification("Singularity", &hints));
        assert!(DbusNotificationMonitor::is_self_notification("WhatsApp (Phone)", &hints));

        hints.insert("singularity-origin".to_string(), OwnedValue::try_from(zbus::zvariant::Value::from("phone")).unwrap());
        assert!(DbusNotificationMonitor::is_self_notification("AnyApp", &hints));
    }
}
