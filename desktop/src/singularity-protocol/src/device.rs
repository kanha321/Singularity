use serde::{Deserialize, Serialize};

/// Telemetry and identity metadata of a target connected device.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct DeviceInfo {
    pub serial: String,
    pub model: String,
    pub android_version: String,
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn device_info_serialization_roundtrip() {
        let info = DeviceInfo {
            serial: "10.235.203.184:5555".into(),
            model: "POCO F5".into(),
            android_version: "16".into(),
        };
        let json = serde_json::to_string(&info).expect("Failed to serialize DeviceInfo");
        let parsed: DeviceInfo = serde_json::from_str(&json).expect("Failed to deserialize DeviceInfo");
        assert_eq!(info, parsed);
    }
}
