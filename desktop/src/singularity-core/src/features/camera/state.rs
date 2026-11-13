use std::sync::{Arc, RwLock};
use singularity_protocol::{CameraLens, CameraResolution, CameraStatus};

#[derive(Debug, Clone)]
struct InnerState {
    active: bool,
    lens: CameraLens,
    resolution: CameraResolution,
    fps: u32,
    torch: bool,
    zoom: f32,
    has_mic: bool,
    readers_count: u32,
}

/// Thread-safe runtime state of the Continuity Camera feature.
#[derive(Debug, Clone)]
pub struct CameraState {
    inner: Arc<RwLock<InnerState>>,
}

impl Default for CameraState {
    fn default() -> Self {
        Self::new()
    }
}

impl CameraState {
    pub fn new() -> Self {
        Self {
            inner: Arc::new(RwLock::new(InnerState {
                active: false,
                lens: CameraLens::Main,
                resolution: CameraResolution::P1080,
                fps: 60,
                torch: false,
                zoom: 1.0,
                has_mic: false,
                readers_count: 0,
            })),
        }
    }

    pub fn is_active(&self) -> bool {
        self.inner.read().map(|s| s.active).unwrap_or(false)
    }

    pub fn set_active(&self, active: bool) {
        if let Ok(mut s) = self.inner.write() {
            s.active = active;
        }
    }

    pub fn lens(&self) -> CameraLens {
        self.inner.read().map(|s| s.lens).unwrap_or(CameraLens::Main)
    }

    pub fn set_lens(&self, lens: CameraLens) {
        if let Ok(mut s) = self.inner.write() {
            s.lens = lens;
        }
    }

    pub fn set_torch(&self, torch: bool) {
        if let Ok(mut s) = self.inner.write() {
            s.torch = torch;
        }
    }

    pub fn set_zoom(&self, zoom: f32) -> f32 {
        let clamped = zoom.clamp(0.65, 10.0);
        if let Ok(mut s) = self.inner.write() {
            s.zoom = clamped;
        }
        clamped
    }

    pub fn set_readers_count(&self, count: u32) {
        if let Ok(mut s) = self.inner.write() {
            s.readers_count = count;
        }
    }

    pub fn set_mic_active(&self, has_mic: bool) {
        if let Ok(mut s) = self.inner.write() {
            s.has_mic = has_mic;
        }
    }

    pub fn to_status(&self) -> CameraStatus {
        let s = self.inner.read().unwrap();
        CameraStatus {
            active: s.active,
            lens: s.lens,
            resolution: s.resolution,
            fps: s.fps,
            torch: s.torch,
            zoom: s.zoom,
            has_mic: s.has_mic,
            readers_count: s.readers_count,
            active_device: "/dev/video10".into(),
            error_message: None,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn state_transitions_and_status() {
        let state = CameraState::new();
        assert!(!state.is_active());

        state.set_active(true);
        state.set_lens(CameraLens::Ultrawide);
        state.set_torch(true);
        state.set_mic_active(true);
        state.set_readers_count(1);

        let status = state.to_status();
        assert!(status.active);
        assert_eq!(status.lens, CameraLens::Ultrawide);
        assert!(status.torch);
        assert!(status.has_mic);
        assert_eq!(status.readers_count, 1);
    }

    #[test]
    fn zoom_level_clamping() {
        let state = CameraState::new();
        assert_eq!(state.set_zoom(0.1), 0.65);
        assert_eq!(state.set_zoom(15.0), 10.0);
        assert_eq!(state.set_zoom(2.5), 2.5);
    }
}

