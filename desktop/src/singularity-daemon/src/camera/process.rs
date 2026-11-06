use std::process::Stdio;
use tokio::process::{Child, Command};
use singularity_protocol::CameraConfig;
use crate::error::{DaemonError, Result};
use super::audio::DEFAULT_MIC_SINK_NAME;

/// Active streaming session handles.
pub struct StreamSession {
    pub child: Child,
    pub mic: Option<super::audio::VirtualMicHandles>,
    #[allow(dead_code)]
    pub config: CameraConfig,
}

/// Constructs command line arguments for the scrcpy camera streaming process.
pub fn build_scrcpy_args(serial: &str, config: &CameraConfig) -> Vec<String> {
    let mut args = vec![
        "-s".to_string(),
        serial.to_string(),
        "--video-source=camera".to_string(),
        format!("--camera-id={}", config.lens.camera_id()),
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

/// Spawns the scrcpy camera process in the background.
pub fn spawn_scrcpy_stream(serial: &str, config: &CameraConfig) -> Result<Child> {
    let args = build_scrcpy_args(serial, config);
    let mut cmd = Command::new("scrcpy");
    cmd.args(&args)
        .stdin(Stdio::null())
        .stdout(Stdio::null())
        .stderr(Stdio::inherit());

    cmd.env("SDL_VIDEODRIVER", "dummy");

    if config.enable_mic {
        cmd.env("PIPEWIRE_NODE", DEFAULT_MIC_SINK_NAME);
        cmd.env("PULSE_SINK", DEFAULT_MIC_SINK_NAME);
    }

    cmd.spawn().map_err(|e| DaemonError::Camera(format!("Failed to spawn scrcpy camera stream: {e}")))
}

/// Gracefully terminates the scrcpy process using SIGTERM, falling back to SIGKILL.
pub async fn terminate_scrcpy_child(child: &mut Child) {
    match child.try_wait() {
        Ok(Some(_)) => return,
        _ => {}
    }
    if let Some(pid) = child.id() {
        unsafe {
            libc::kill(pid as libc::pid_t, libc::SIGTERM);
        }
        if tokio::time::timeout(std::time::Duration::from_millis(600), child.wait()).await.is_err() {
            let _ = child.kill().await;
            let _ = child.wait().await;
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use singularity_protocol::{CameraLens, CameraResolution};

    #[test]
    fn build_args_default_main_camera_with_mic() {
        let config = CameraConfig::default();
        let args = build_scrcpy_args("10.194.45.144:5555", &config);

        assert!(args.contains(&"-s".into()));
        assert!(args.contains(&"10.194.45.144:5555".into()));
        assert!(args.contains(&"--video-source=camera".into()));
        assert!(args.contains(&"--camera-id=0".into()));
        assert!(args.contains(&"--camera-size=1920x1080".into()));
        assert!(args.contains(&"--camera-fps=30".into()));
        assert!(args.contains(&"--no-video-playback".into()));
        assert!(args.contains(&"--v4l2-sink=/dev/video10".into()));
        assert!(args.contains(&"--audio-source=mic-voice-communication".into()));
    }

    #[test]
    fn build_args_ultrawide_no_mic() {
        let mut config = CameraConfig::default();
        config.lens = CameraLens::Ultrawide;
        config.resolution = CameraResolution::P4K;
        config.fps = 30;
        config.enable_mic = false;

        let args = build_scrcpy_args("device123", &config);
        assert!(args.contains(&"--camera-id=4".into()));
        assert!(args.contains(&"--camera-size=3840x2160".into()));
        assert!(args.contains(&"--camera-fps=30".into()));
        assert!(args.contains(&"--no-audio".into()));
        assert!(!args.contains(&"--audio-source=mic-voice-communication".into()));
    }
}
