use serde::{Deserialize, Serialize};

/// Telemetry and identity metadata of a target connected device.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct DeviceInfo {
    pub serial: String,
    pub model: String,
    pub android_version: String,
}

/// Device discovered or connected via ADB or network beacon.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct DeviceEntry {
    pub serial: String,
    pub model: Option<String>,
    pub connection_type: String,
    pub is_active: bool,
    pub is_paired: bool,
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn device_info_serialization_roundtrip() {
        let info = DeviceInfo {
            serial: "10.235.203.184:5555".into(),
            model: "Pixel 8".into(),
            android_version: "16".into(),
        };
        let json = serde_json::to_string(&info).expect("Failed to serialize DeviceInfo");
        let parsed: DeviceInfo = serde_json::from_str(&json).expect("Failed to deserialize DeviceInfo");
        assert_eq!(info, parsed);
    }

    #[test]
    fn device_entry_serialization_roundtrip() {
        let entry = DeviceEntry {
            serial: "23049PCD8I".into(),
            model: Some("Pixel 8".into()),
            connection_type: "usb".into(),
            is_active: true,
            is_paired: true,
        };
        let json = serde_json::to_string(&entry).unwrap();
        let parsed: DeviceEntry = serde_json::from_str(&json).unwrap();
        assert_eq!(entry, parsed);
    }
}
