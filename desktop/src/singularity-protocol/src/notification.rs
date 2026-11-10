use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct NotificationButton {
    pub action_id: String,
    pub label: String,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct PhoneNotification {
    pub id: String,
    pub package_name: String,
    pub app_name: String,
    pub title: String,
    pub text: String,
    pub timestamp: u64,
    #[serde(default = "default_true")]
    pub is_clearable: bool,
    #[serde(default)]
    pub can_reply: bool,
    #[serde(default)]
    pub actions: Vec<NotificationButton>,
    #[serde(default)]
    pub icon_base64: Option<String>,
    #[serde(default)]
    pub group_key: Option<String>,
}

fn default_true() -> bool {
    true
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct DesktopNotification {
    pub id: u32,
    pub app_name: String,
    pub summary: String,
    pub body: String,
    #[serde(default)]
    pub urgency: u8,
    #[serde(default = "default_timeout")]
    pub timeout_ms: i32,
}

fn default_timeout() -> i32 {
    5000
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct NotificationDismiss {
    pub id: String,
    pub source: String,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct NotificationActionInvoke {
    pub id: String,
    pub action_id: String,
    #[serde(default)]
    pub reply_text: Option<String>,
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_phone_notification_roundtrip() {
        let notif = PhoneNotification {
            id: "0|com.whatsapp|123|null|1000".into(),
            package_name: "com.whatsapp".into(),
            app_name: "WhatsApp".into(),
            title: "Alice".into(),
            text: "See you at 5pm".into(),
            timestamp: 1730000000,
            is_clearable: true,
            can_reply: true,
            actions: vec![NotificationButton {
                action_id: "reply_0".into(),
                label: "Reply".into(),
            }],
            icon_base64: Some("base64data".into()),
            group_key: Some("group123".into()),
        };
        let json = serde_json::to_string(&notif).unwrap();
        let parsed: PhoneNotification = serde_json::from_str(&json).unwrap();
        assert_eq!(notif, parsed);
    }

    #[test]
    fn test_desktop_notification_roundtrip() {
        let notif = DesktopNotification {
            id: 42,
            app_name: "Terminal".into(),
            summary: "Build finished".into(),
            body: "Completed in 4.2s".into(),
            urgency: 1,
            timeout_ms: 5000,
        };
        let json = serde_json::to_string(&notif).unwrap();
        let parsed: DesktopNotification = serde_json::from_str(&json).unwrap();
        assert_eq!(notif, parsed);
    }

    #[test]
    fn test_notification_dismiss_roundtrip() {
        let dismiss = NotificationDismiss {
            id: "42".into(),
            source: "desktop".into(),
        };
        let json = serde_json::to_string(&dismiss).unwrap();
        let parsed: NotificationDismiss = serde_json::from_str(&json).unwrap();
        assert_eq!(dismiss, parsed);
    }

    #[test]
    fn test_notification_action_invoke_roundtrip() {
        let action = NotificationActionInvoke {
            id: "123".into(),
            action_id: "reply".into(),
            reply_text: Some("Hello there".into()),
        };
        let json = serde_json::to_string(&action).unwrap();
        let parsed: NotificationActionInvoke = serde_json::from_str(&json).unwrap();
        assert_eq!(action, parsed);
    }
}

