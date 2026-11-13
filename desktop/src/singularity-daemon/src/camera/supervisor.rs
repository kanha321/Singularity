use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use tokio::sync::Mutex;
use singularity_protocol::{CameraConfig, CameraLens, CameraStatus};
use crate::error::{DaemonError, Result};
use super::audio::{setup_virtual_microphone, teardown_virtual_microphone};
use super::process::{check_stream_health, spawn_scrcpy_stream, terminate_scrcpy_child, StreamSession};
use super::v4l2::{count_external_readers, ensure_v4l2_sustained, refresh_wireplumber, resolve_v4l2_device};

#[derive(Clone, Default)]
pub struct CameraSupervisor {
    session: Arc<Mutex<Option<StreamSession>>>,
    last_config: Arc<Mutex<CameraConfig>>,
    last_serial: Arc<Mutex<String>>,
    torch: Arc<Mutex<bool>>, zoom: Arc<Mutex<f32>>,
    is_switching: Arc<AtomicBool>,
    last_error: Arc<Mutex<Option<String>>>,
}

impl CameraSupervisor {
    pub fn new() -> Self { Self { zoom: Arc::new(Mutex::new(1.0)), ..Default::default() } }

    pub async fn is_streaming(&self) -> bool {
        if self.is_switching.load(Ordering::SeqCst) { return true; }
        let mut guard = self.session.lock().await;
        match guard.as_mut().map(|s| s.child.try_wait()) {
            Some(Ok(None)) => true,
            Some(_) => {
                let _ = Self::stop_internal(&mut guard, "").await;
                let mut err = self.last_error.lock().await;
                if err.is_none() { *err = Some("Webcam stream exited unexpectedly".into()); }
                false
            }
            None => false,
        }
    }

    pub async fn start(&self, serial: &str, config: Option<CameraConfig>) -> Result<CameraStatus> {
        *self.last_serial.lock().await = serial.to_string();
        let mut guard = self.session.lock().await;
        if guard.is_some() { Self::stop_internal(&mut guard, serial).await?; }
        let mut cfg = config.unwrap_or_default();
        if resolve_v4l2_device(&cfg.device_path).is_none() { let _ = super::polkit::auto_load_v4l2_module().await; }
        cfg.device_path = resolve_v4l2_device(&cfg.device_path).ok_or_else(|| DaemonError::Camera(format!("Virtual device '{}' not found", cfg.device_path)))?;

        let discovered = super::probe::probe_device_cameras(serial).await;
        cfg.dynamic_camera_id = Some(super::probe::resolve_lens_id(&discovered, cfg.lens));
        *self.last_config.lock().await = cfg.clone();

        super::process::kill_android_camera_server(serial).await;
        ensure_v4l2_sustained(&cfg.device_path);
        let mic_handles = if cfg.enable_mic { setup_virtual_microphone().ok() } else { None };
        let mut child = spawn_scrcpy_stream(serial, &cfg)?;

        if let Err(e) = check_stream_health(&mut child, serial, 1200).await {
            if let Some(m) = mic_handles { let _ = teardown_virtual_microphone(&m); }
            *self.last_error.lock().await = Some(e.to_string());
            return Err(e);
        }
        *self.last_error.lock().await = None;
        let pid = child.id();
        *guard = Some(StreamSession { child, mic: mic_handles.clone(), config: cfg.clone() });
        drop(guard);
        refresh_wireplumber().await;
        Ok(self.status_internal(&cfg, true, mic_handles.is_some(), pid, None).await)
    }

    pub async fn stop(&self) -> Result<()> {
        let serial = self.last_serial.lock().await.clone();
        Self::stop_internal(&mut *self.session.lock().await, &serial).await
    }
    async fn stop_internal(session: &mut Option<StreamSession>, serial: &str) -> Result<()> {
        if let Some(mut s) = session.take() {
            terminate_scrcpy_child(&mut s.child).await;
            if let Some(mic) = s.mic { let _ = teardown_virtual_microphone(&mic); }
            super::process::kill_android_camera_server(serial).await;
            tokio::time::sleep(std::time::Duration::from_millis(200)).await;
            refresh_wireplumber().await;
        }
        Ok(())
    }

    pub async fn switch_lens(&self, raw_serial: &str, lens: CameraLens) -> Result<CameraStatus> {
        let s_fallback = self.last_serial.lock().await.clone();
        let serial = if raw_serial.is_empty() || raw_serial == "unknown" { &s_fallback } else { raw_serial };
        let mut cfg = self.last_config.lock().await.clone();
        cfg.lens = lens;
        let disc = super::probe::probe_device_cameras(serial).await;
        cfg.dynamic_camera_id = Some(super::probe::resolve_lens_id(&disc, lens));
        *self.last_config.lock().await = cfg.clone();
        if !self.is_streaming().await { return Ok(self.status_internal(&cfg, false, false, None, None).await); }

        self.is_switching.store(true, Ordering::SeqCst);
        ensure_v4l2_sustained(&cfg.device_path);
        let mut guard = self.session.lock().await;
        let old_mic = if let Some(mut s) = guard.take() {
            terminate_scrcpy_child(&mut s.child).await;
            super::process::kill_android_camera_server(serial).await;
            tokio::time::sleep(std::time::Duration::from_millis(400)).await;
            s.mic
        } else { None };

        let mut child = match spawn_scrcpy_stream(serial, &cfg) {
            Ok(c) => c,
            Err(e) => {
                if let Some(ref m) = old_mic { let _ = teardown_virtual_microphone(m); }
                self.is_switching.store(false, Ordering::SeqCst);
                return Err(e);
            }
        };

        if let Err(e) = check_stream_health(&mut child, serial, 800).await {
            if let Some(ref m) = old_mic { let _ = teardown_virtual_microphone(m); }
            self.is_switching.store(false, Ordering::SeqCst);
            *self.last_error.lock().await = Some(e.to_string());
            return Err(e);
        }

        self.is_switching.store(false, Ordering::SeqCst);
        *self.last_error.lock().await = None;
        let pid = child.id();
        *guard = Some(StreamSession { child, mic: old_mic.clone(), config: cfg.clone() });
        drop(guard);
        refresh_wireplumber().await;
        Ok(self.status_internal(&cfg, true, old_mic.is_some(), pid, None).await)
    }

    pub async fn set_torch(&self, enabled: bool) -> Result<()> { *self.torch.lock().await = enabled; Ok(()) }
    pub async fn set_zoom(&self, z: f32) -> Result<f32> { let c = z.clamp(0.65, 10.0); *self.zoom.lock().await = c; Ok(c) }
    pub async fn status(&self) -> CameraStatus {
        let is_active = self.is_streaming().await;
        let cfg = self.last_config.lock().await.clone();
        let guard = self.session.lock().await;
        let (has_mic, pid) = (guard.as_ref().and_then(|s| s.mic.as_ref()).is_some(), guard.as_ref().and_then(|s| s.child.id()));
        drop(guard);
        let err = if is_active { None } else { self.last_error.lock().await.clone() };
        self.status_internal(&cfg, is_active, has_mic, pid, err).await
    }

    async fn status_internal(&self, cfg: &CameraConfig, active: bool, has_mic: bool, pid: Option<u32>, err: Option<String>) -> CameraStatus {
        let ignored: Vec<u32> = [Some(std::process::id()), pid].into_iter().flatten().collect();
        CameraStatus {
            active, lens: cfg.lens, resolution: cfg.resolution, fps: cfg.fps,
            torch: *self.torch.lock().await, zoom: *self.zoom.lock().await, has_mic,
            readers_count: count_external_readers(&cfg.device_path, &ignored),
            active_device: cfg.device_path.clone(), error_message: err,
        }
    }
}
