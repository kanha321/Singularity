use std::collections::HashMap;
use tokio::process::Command;
use zbus::zvariant::Value;
use zbus::Connection;
use singularity_protocol::PhoneNotification;

pub struct DbusNotificationSender {
    conn: Option<Connection>,
}

impl DbusNotificationSender {
    pub async fn new() -> Self {
        Self { conn: Connection::session().await.ok() }
    }

    pub async fn post_notification(&self, notif: &PhoneNotification, replaces_id: u32, is_locked: bool) -> u32 {
        let clean_name = Self::clean_app_name(&notif.app_name, &notif.package_name);
        let app_label = format!("{} (Phone)", clean_name);
        let icon_path = super::icon_cache::get_or_save_icon(&notif.package_name, notif.icon_base64.as_deref());
        let icon_str = icon_path.as_deref().unwrap_or("phone");
        let body_text = if is_locked { "•••••• (Locked)" } else { notif.text.as_str() };

        let mut actions: Vec<&str> = Vec::new();
        if notif.can_reply && !is_locked {
            actions.push("reply");
            actions.push("💬 Reply");
        }
        for btn in &notif.actions {
            if notif.can_reply && btn.label.eq_ignore_ascii_case("reply") {
                continue;
            }
            actions.push(btn.action_id.as_str());
            actions.push(btn.label.as_str());
        }

        if let Some(ref conn) = self.conn {
            if let Ok(proxy) = zbus::Proxy::new(conn, "org.freedesktop.Notifications", "/org/freedesktop/Notifications", "org.freedesktop.Notifications").await {
                let mut hints: HashMap<&str, Value> = HashMap::new();
                hints.insert("singularity-origin", Value::from("phone"));
                hints.insert("resident", Value::from(true));

                let res: std::result::Result<u32, zbus::Error> = proxy.call(
                    "Notify",
                    &(app_label.as_str(), replaces_id, icon_str, notif.title.as_str(), body_text, actions, hints, 15000i32),
                ).await;

                if let Ok(id) = res { return id; }
            }
        }
        Self::fallback_notify_send(&app_label, &notif.title, body_text, icon_str, replaces_id).await
    }

    fn clean_app_name(app_name: &str, package_name: &str) -> String {
        let name = app_name.trim();
        if !name.is_empty() && !name.contains('.') {
            return name.to_string();
        }
        let target = if !name.is_empty() { name } else { package_name };
        let parts: Vec<&str> = target.split('.').collect();
        for part in parts.iter().rev() {
            let p = part.to_ascii_lowercase();
            if p != "android" && p != "app" && p != "mobile" && p != "apps" && p != "shopping" && p != "mshop" {
                let mut chars = part.chars();
                if let Some(first) = chars.next() {
                    return format!("{}{}", first.to_uppercase(), chars.as_str());
                }
            }
        }
        "Phone".into()
    }

    pub async fn close_notification(&self, id: u32) {
        if id == 0 { return; }
        if let Some(ref conn) = self.conn {
            if let Ok(proxy) = zbus::Proxy::new(conn, "org.freedesktop.Notifications", "/org/freedesktop/Notifications", "org.freedesktop.Notifications").await {
                let _: std::result::Result<(), _> = proxy.call("CloseNotification", &(id,)).await;
            }
        }
    }

    async fn fallback_notify_send(app_name: &str, title: &str, text: &str, icon: &str, replaces_id: u32) -> u32 {
        let mut cmd = Command::new("notify-send");
        cmd.args(["-a", app_name, "-i", icon, "-p"]);
        if replaces_id > 0 { cmd.args(["-r", &replaces_id.to_string()]); }
        cmd.arg(title).arg(text);

        if let Ok(out) = cmd.output().await {
            if out.status.success() {
                if let Ok(parsed) = String::from_utf8_lossy(&out.stdout).trim().parse::<u32>() {
                    return parsed;
                }
            }
        }
        0
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn test_dbus_sender_initialization() {
        let sender = DbusNotificationSender::new().await;
        let notif = PhoneNotification {
            id: "test_key".into(), package_name: "com.test".into(), app_name: "TestApp".into(),
            title: "Hello".into(), text: "World".into(), timestamp: 0, is_clearable: true,
            can_reply: true, actions: vec![], icon_base64: None, group_key: None,
        };
        let _id = sender.post_notification(&notif, 0, false).await;
    }

    #[test]
    fn test_clean_app_name() {
        assert_eq!(DbusNotificationSender::clean_app_name("WhatsApp", "com.whatsapp"), "WhatsApp");
        assert_eq!(DbusNotificationSender::clean_app_name("", "com.linkedin.android"), "Linkedin");
        assert_eq!(DbusNotificationSender::clean_app_name("in.amazon.mShop.android.shopping", "in.amazon.mShop.android.shopping"), "Amazon");
        assert_eq!(DbusNotificationSender::clean_app_name("", "com.android.shell"), "Shell");
    }
}
