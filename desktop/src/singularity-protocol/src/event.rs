use serde::{Deserialize, Serialize};

/// High-level typed domain events dispatched over the central DeviceBridge event bus.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(tag = "type", content = "payload")]
pub enum DeviceEvent {
    /// Remote clipboard updated on Android device
    ClipboardChanged {
        text: String,
        sequence: u64,
    },

    /// Incoming call alert detected
    IncomingCall {
        caller: String,
        number: String,
    },

    /// Call state transitioned (Ringing, OffHook, Idle)
    CallStateChanged {
        state: String,
    },

    /// Telemetry update (Battery %, wattage, charging state)
    BatteryUpdate {
        percentage: u8,
        temperature: f32,
        is_charging: bool,
    },

    /// SMS OTP code extracted
    OtpReceived {
        code: String,
        sender: String,
    },

    /// Notification posted by an app
    NotificationPosted {
        package_name: String,
        title: String,
        text: String,
        can_reply: bool,
    },

    /// Media playback metadata or state changed
    MediaUpdated {
        title: String,
        artist: String,
        is_playing: bool,
    },

    /// Network storage (Root SFTP / FUSE) successfully mounted
    StorageMounted {
        mount_point: String,
    },

    /// Network storage (Root SFTP / FUSE) unmounted
    StorageUnmounted,
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn clipboard_event_serializes_to_tagged_json() {
        let event = DeviceEvent::ClipboardChanged {
            text: "hello world".into(),
            sequence: 42,
        };
        let json = serde_json::to_string(&event).expect("serialization failed");
        assert!(json.contains("\"type\":\"ClipboardChanged\""));
        assert!(json.contains("\"text\":\"hello world\""));
    }

    #[test]
    fn incoming_call_roundtrip_deserialization() {
        let original = DeviceEvent::IncomingCall {
            caller: "Alice".into(),
            number: "+123456789".into(),
        };
        let json = serde_json::to_string(&original).unwrap();
        let parsed: DeviceEvent = serde_json::from_str(&json).unwrap();
        assert_eq!(original, parsed);
    }
}
