use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use tokio::sync::Mutex;
use singularity_protocol::{CameraConfig, CameraLens, CameraStatus};
use crate::error::Result;
use super::audio::{setup_virtual_microphone, teardown_virtual_microphone};
use super::process::{spawn_scrcpy_stream, terminate_scrcpy_child, StreamSession};
use super::v4l2::{count_external_readers, ensure_v4l2_sustained};

#[derive(Clone)]
pub struct CameraSupervisor {
    session: Arc<Mutex<Option<StreamSession>>>,
    last_config: Arc<Mutex<CameraConfig>>,
    last_serial: Arc<Mutex<String>>,
    torch: Arc<Mutex<bool>>,
    zoom: Arc<Mutex<f32>>,
    is_switching: Arc<AtomicBool>,
}

impl Default for CameraSupervisor {
    fn default() -> Self { Self::new() }
}

impl CameraSupervisor {
    pub fn new() -> Self {
        Self {
            session: Arc::new(Mutex::new(None)),
            last_config: Arc::new(Mutex::new(CameraConfig::default())),
            last_serial: Arc::new(Mutex::new(String::new())),
            torch: Arc::new(Mutex::new(false)),
            zoom: Arc::new(Mutex::new(1.0)),
            is_switching: Arc::new(AtomicBool::new(false)),
        }
    }

    pub async fn is_streaming(&self) -> bool {
        if self.is_switching.load(Ordering::SeqCst) { return true; }
        let mut guard = self.session.lock().await;
        match guard.as_mut().map(|s| s.child.try_wait()) {
            Some(Ok(None)) => true,
            Some(_) => { let _ = Self::stop_internal(&mut guard, "").await; false }
            None => false,
        }
    }

    pub async fn start(&self, serial: &str, config: Option<CameraConfig>) -> Result<CameraStatus> {
        *self.last_serial.lock().await = serial.to_string();
        let mut guard = self.session.lock().await;
        if guard.is_some() { Self::stop_internal(&mut guard, serial).await?; }

        let cfg = config.unwrap_or_else(CameraConfig::default);
        *self.last_config.lock().await = cfg.clone();
        ensure_v4l2_sustained(&cfg.device_path);

        let mic_handles = if cfg.enable_mic { setup_virtual_microphone().ok() } else { None };
        let child = spawn_scrcpy_stream(serial, &cfg)?;
        let child_pid = child.id();
        *guard = Some(StreamSession { child, mic: mic_handles.clone(), config: cfg.clone() });
        drop(guard);

        Ok(self.status_internal(&cfg, true, mic_handles.is_some(), child_pid).await)
    }

    pub async fn stop(&self) -> Result<()> {
        let serial = self.last_serial.lock().await.clone();
        let mut guard = self.session.lock().await;
        Self::stop_internal(&mut guard, &serial).await
    }

    async fn stop_internal(session: &mut Option<StreamSession>, serial: &str) -> Result<()> {
        if let Some(mut s) = session.take() {
            terminate_scrcpy_child(&mut s.child).await;
            if let Some(mic) = s.mic { let _ = teardown_virtual_microphone(&mic); }
            if !serial.is_empty() && serial != "auto" {
                let _ = tokio::process::Command::new("adb")
                    .args(["-s", serial, "shell", "pkill -f 'video_source=camera'"])
                    .status().await;
            }
            tokio::time::sleep(std::time::Duration::from_millis(200)).await;
        }
        Ok(())
    }

    pub async fn switch_lens(&self, serial: &str, lens: CameraLens) -> Result<CameraStatus> {
        let mut cfg = self.last_config.lock().await.clone();
        cfg.lens = lens;
        *self.last_config.lock().await = cfg.clone();
        if !self.is_streaming().await {
            return Ok(self.status_internal(&cfg, false, false, None).await);
        }

        self.is_switching.store(true, Ordering::SeqCst);
        ensure_v4l2_sustained(&cfg.device_path);

        let mut guard = self.session.lock().await;
        let old_mic = if let Some(mut s) = guard.take() {
            terminate_scrcpy_child(&mut s.child).await;
            s.mic
        } else {
            None
        };
        tokio::time::sleep(std::time::Duration::from_millis(200)).await;

        let res = spawn_scrcpy_stream(serial, &cfg);
        let status = match res {
            Ok(child) => {
                let pid = child.id();
                *guard = Some(StreamSession { child, mic: old_mic.clone(), config: cfg.clone() });
                self.status_internal(&cfg, true, old_mic.is_some(), pid).await
            }
            Err(e) => {
                if let Some(ref m) = old_mic { let _ = teardown_virtual_microphone(m); }
                self.is_switching.store(false, Ordering::SeqCst);
                return Err(e);
            }
        };
        self.is_switching.store(false, Ordering::SeqCst);
        Ok(status)
    }

    pub async fn set_torch(&self, enabled: bool) -> Result<()> { *self.torch.lock().await = enabled; Ok(()) }
    pub async fn set_zoom(&self, zoom: f32) -> Result<f32> {
        let clamped = zoom.clamp(0.65, 10.0);
        *self.zoom.lock().await = clamped;
        Ok(clamped)
    }

    pub async fn status(&self) -> CameraStatus {
        let is_active = self.is_streaming().await;
        let cfg = self.last_config.lock().await.clone();
        let guard = self.session.lock().await;
        let has_mic = guard.as_ref().and_then(|s| s.mic.as_ref()).is_some();
        let child_pid = guard.as_ref().and_then(|s| s.child.id());
        drop(guard);
        self.status_internal(&cfg, is_active, has_mic, child_pid).await
    }

    async fn status_internal(&self, cfg: &CameraConfig, active: bool, has_mic: bool, pid: Option<u32>) -> CameraStatus {
        let mut ignored = vec![std::process::id()];
        if let Some(p) = pid { ignored.push(p); }
        let readers = count_external_readers(&cfg.device_path, &ignored);
        CameraStatus {
            active, lens: cfg.lens, resolution: cfg.resolution,
            fps: cfg.fps, torch: *self.torch.lock().await, zoom: *self.zoom.lock().await,
            has_mic, readers_count: readers,
        }
    }
}
