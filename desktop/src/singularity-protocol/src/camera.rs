use serde::{Deserialize, Serialize};

/// Supported camera sensors on the device.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum CameraLens {
    Main,
    Ultrawide,
    Front,
    Custom(u32),
}

impl CameraLens {
    pub fn camera_id(&self) -> u32 {
        match self {
            Self::Main => 0,
            Self::Ultrawide => 4,
            Self::Front => 1,
            Self::Custom(id) => *id,
        }
    }

    pub fn facing(&self) -> &'static str {
        match self {
            Self::Main | Self::Ultrawide | Self::Custom(_) => "back",
            Self::Front => "front",
        }
    }
}

/// Video streaming capture resolutions.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum CameraResolution {
    #[serde(rename = "1080p")]
    P1080,
    #[serde(rename = "4k")]
    P4K,
    #[serde(rename = "720p")]
    P720,
}

impl CameraResolution {
    pub fn dimensions(&self) -> (u32, u32) {
        match self {
            Self::P1080 => (1920, 1080),
            Self::P4K => (3840, 2160),
            Self::P720 => (1280, 720),
        }
    }

    pub fn as_str(&self) -> &'static str {
        match self {
            Self::P1080 => "1920x1080",
            Self::P4K => "3840x2160",
            Self::P720 => "1280x720",
        }
    }
}

/// Configuration parameters for streaming webcam feed.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct CameraConfig {
    pub lens: CameraLens,
    pub resolution: CameraResolution,
    pub fps: u32,
    pub enable_mic: bool,
    pub device_path: String,
    pub auto_demand: bool,
}

impl Default for CameraConfig {
    fn default() -> Self {
        Self {
            lens: CameraLens::Main, resolution: CameraResolution::P1080,
            fps: 30, enable_mic: true,
            device_path: "/dev/video10".into(), auto_demand: true,
        }
    }
}

/// Live status of the virtual camera device.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct CameraStatus {
    pub active: bool,
    pub lens: CameraLens,
    pub resolution: CameraResolution,
    pub fps: u32,
    pub torch: bool,
    pub zoom: f32,
    pub has_mic: bool,
    pub readers_count: u32,
}

impl Default for CameraStatus {
    fn default() -> Self {
        Self {
            active: false,
            lens: CameraLens::Main,
            resolution: CameraResolution::P1080,
            fps: 30,
            torch: false,
            zoom: 1.0,
            has_mic: false,
            readers_count: 0,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn lens_defaults_and_resolutions() {
        assert_eq!(CameraLens::Main.camera_id(), 0);
        assert_eq!(CameraLens::Main.facing(), "back");
        assert_eq!(CameraLens::Ultrawide.camera_id(), 4);
        assert_eq!(CameraLens::Front.camera_id(), 1);
        assert_eq!(CameraLens::Front.facing(), "front");
        assert_eq!(CameraLens::Custom(9).camera_id(), 9);
        assert_eq!(CameraResolution::P1080.dimensions(), (1920, 1080));
        assert_eq!(CameraResolution::P1080.as_str(), "1920x1080");
        assert_eq!(CameraResolution::P4K.dimensions(), (3840, 2160));
        assert_eq!(CameraResolution::P720.dimensions(), (1280, 720));
    }

    #[test]
    fn camera_models_json_roundtrip() {
        let config = CameraConfig::default();
        let json = serde_json::to_string(&config).unwrap();
        let decoded: CameraConfig = serde_json::from_str(&json).unwrap();
        assert_eq!(config, decoded);

        let status = CameraStatus {
            active: true,
            lens: CameraLens::Ultrawide,
            resolution: CameraResolution::P4K,
            fps: 30,
            torch: true,
            zoom: 0.65,
            has_mic: true,
            readers_count: 2,
        };
        let s_json = serde_json::to_string(&status).unwrap();
        let s_decoded: CameraStatus = serde_json::from_str(&s_json).unwrap();
        assert_eq!(status, s_decoded);
    }
}
