use serde::{Deserialize, Serialize};
use singularity_protocol::{CameraConfig, CameraLens, CameraResolution};

/// Core configuration for the continuity camera feature.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct FeatureCameraConfig {
    pub enabled: bool,
    pub stream: CameraConfig,
}

impl Default for FeatureCameraConfig {
    fn default() -> Self {
        Self {
            enabled: true,
            stream: CameraConfig::default(),
        }
    }
}

impl FeatureCameraConfig {
    pub fn new(enabled: bool, lens: CameraLens, resolution: CameraResolution, fps: u32) -> Self {
        let stream = CameraConfig {
            lens,
            resolution,
            fps,
            enable_mic: true,
            device_path: "/dev/video10".into(),
            auto_demand: true,
        };
        Self { enabled, stream }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn default_config_values() {
        let cfg = FeatureCameraConfig::default();
        assert!(cfg.enabled);
        assert_eq!(cfg.stream.lens, CameraLens::Main);
        assert_eq!(cfg.stream.resolution, CameraResolution::P1080);
        assert_eq!(cfg.stream.fps, 30);
        assert!(cfg.stream.enable_mic);
        assert!(cfg.stream.auto_demand);
        assert_eq!(cfg.stream.device_path, "/dev/video10");
    }

    #[test]
    fn custom_config_creation() {
        let cfg = FeatureCameraConfig::new(
            false,
            CameraLens::Ultrawide,
            CameraResolution::P4K,
            30,
        );
        assert!(!cfg.enabled);
        assert_eq!(cfg.stream.lens, CameraLens::Ultrawide);
        assert_eq!(cfg.stream.resolution, CameraResolution::P4K);
        assert_eq!(cfg.stream.fps, 30);
    }
}

