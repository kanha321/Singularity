use std::process::Stdio;
use tokio::process::{Child, Command};
use singularity_protocol::CameraConfig;
use crate::error::{DaemonError, Result};
use super::audio::DEFAULT_MIC_SINK_NAME;

pub struct StreamSession {
    pub child: Child,
    pub mic: Option<super::audio::VirtualMicHandles>,
    #[allow(dead_code)]
    pub config: CameraConfig,
}

pub fn build_scrcpy_args(serial: &str, config: &CameraConfig) -> Vec<String> {
    let cam_id = config.dynamic_camera_id.unwrap_or_else(|| config.lens.camera_id());
    let mut args = vec![
        "-s".to_string(), serial.to_string(),
        "--video-source=camera".to_string(),
        format!("--camera-id={}", cam_id),
        format!("--camera-size={}", config.resolution.as_str()),
        format!("--camera-fps={}", config.fps),
        "--no-video-playback".to_string(),
        format!("--v4l2-sink={}", config.device_path),
    ];
    if config.enable_mic {
        args.push("--audio-source=mic-voice-communication".to_string());
        args.push("--audio-output-buffer=50".to_string());
    } else {
        args.push("--no-audio".to_string());
    }
    args
}

#[allow(dead_code)]
pub fn parse_scrcpy_error(stderr: &str) -> String {
    if stderr.contains("Failed to open output device") {
        return "Virtual camera device not found or inaccessible".into();
    }
    if stderr.contains("Failed to write header") || stderr.contains("VIDIOC_G_FMT") {
        return "Camera format locked by an active application (Zoom/Discord/Chrome)".into();
    }
    if stderr.contains("CameraAccessException") || stderr.contains("Too many other clients") {
        return "Camera busy or held by zombie client on Android".into();
    }
    if stderr.contains("Demuxer error") {
        return "Video stream demuxer failed from phone".into();
    }
    stderr.lines().rev().find(|l| !l.trim().is_empty()).unwrap_or("Unknown scrcpy stream failure").to_string()
}

pub fn spawn_scrcpy_stream(serial: &str, config: &CameraConfig) -> Result<Child> {
    let args = build_scrcpy_args(serial, config);
    tracing::info!("Spawning scrcpy stream with args: {:?}", args);
    let mut cmd = Command::new("scrcpy");
    cmd.args(&args)
        .stdin(Stdio::null())
        .stdout(Stdio::null())
        .stderr(Stdio::piped())
        .env("SDL_VIDEODRIVER", "dummy");

    if config.enable_mic {
        cmd.env("PIPEWIRE_NODE", DEFAULT_MIC_SINK_NAME);
        cmd.env("PULSE_SINK", DEFAULT_MIC_SINK_NAME);
    }
    let mut child = cmd.spawn().map_err(|e| DaemonError::Camera(format!("Failed to spawn scrcpy stream: {e}")))?;
    if let Some(stderr) = child.stderr.take() {
        tokio::spawn(async move {
            use tokio::io::AsyncBufReadExt;
            let mut lines = tokio::io::BufReader::new(stderr).lines();
            while let Ok(Some(line)) = lines.next_line().await {
                tracing::info!("[scrcpy] {line}");
            }
        });
    }
    Ok(child)
}

pub async fn check_stream_health(child: &mut Child, serial: &str, wait_ms: u64) -> Result<()> {
    tokio::time::sleep(std::time::Duration::from_millis(wait_ms)).await;
    if let Ok(Some(st)) = child.try_wait() {
        reset_android_cameraserver(serial).await;
        return Err(DaemonError::Camera(format!("scrcpy stream exited on startup (code: {st:?})")));
    }
    Ok(())
}

pub async fn terminate_scrcpy_child(child: &mut Child) {
    if child.try_wait().ok().flatten().is_some() { return; }
    if let Some(pid) = child.id() {
        unsafe { libc::kill(pid as libc::pid_t, libc::SIGTERM); }
        if tokio::time::timeout(std::time::Duration::from_millis(600), child.wait()).await.is_err() {
            let _ = child.kill().await;
            let _ = child.wait().await;
        }
    }
}

pub async fn kill_android_camera_server(serial: &str) {
    if serial.is_empty() || serial == "auto" { return; }
    let _ = Command::new("adb")
        .args(["-s", serial, "shell", "pkill -9 -f '[v]ideo_source=camera' || true"])
        .status().await;
}

pub async fn reset_android_cameraserver(serial: &str) {
    if serial.is_empty() || serial == "auto" { return; }
    let _ = Command::new("adb")
        .args(["-s", serial, "shell", "su -c 'killall -9 cameraserver' 2>/dev/null || pkill -9 -f '[v]ideo_source=camera' || true"])
        .status().await;
    tokio::time::sleep(std::time::Duration::from_millis(300)).await;
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn build_args_includes_dynamic_id() {
        let mut config = CameraConfig::default();
        config.dynamic_camera_id = Some(4);
        let args = build_scrcpy_args("10.0.0.1:5555", &config);
        assert!(args.contains(&"--camera-id=4".into()));
        assert!(!args.contains(&"--turn-screen-off".into()));
    }

    #[test]
    fn test_parse_scrcpy_error_patterns() {
        assert!(parse_scrcpy_error("ERROR: Failed to open output device: /dev/video10").contains("Virtual camera"));
        assert!(parse_scrcpy_error("ERROR: Failed to write header to /dev/video10").to_lowercase().contains("format locked"));
    }
}
