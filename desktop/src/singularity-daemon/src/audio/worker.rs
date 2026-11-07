use std::process::Stdio;
use std::time::Duration;
use tokio::process::{Child, Command};
use crate::audio::volume::{find_scrcpy_sink_input, move_sink_input};
use crate::error::{DaemonError, Result};

pub struct AudioWorker {
    serial: Option<String>,
    current_sink: Option<String>,
    child: Option<Child>,
}

impl AudioWorker {
    pub fn new() -> Self {
        Self { serial: None, current_sink: None, child: None }
    }

    #[allow(dead_code)]
    pub fn current_sink(&self) -> Option<&str> {
        self.current_sink.as_deref()
    }

    pub fn is_active(&mut self) -> bool {
        match self.child.as_mut() {
            Some(c) => c.try_wait().ok().flatten().is_none(),
            None => false,
        }
    }

    pub async fn set_target_sink(&mut self, serial: &str, sink_name: &str) -> Result<()> {
        self.serial = Some(serial.to_string());
        if self.current_sink.as_deref() == Some(sink_name) && self.is_active() {
            return Ok(());
        }

        if self.is_active() {
            let pid = self.child.as_ref().and_then(|c| c.id());
            if let Some(input_id) = find_scrcpy_sink_input(pid).await {
                if move_sink_input(input_id, sink_name).await.is_ok() {
                    self.current_sink = Some(sink_name.to_string());
                    return Ok(());
                }
            }
        }

        self.stop().await;
        self.serial = Some(serial.to_string());
        let child = spawn_audio_scrcpy(serial, sink_name)?;
        self.child = Some(child);
        self.current_sink = Some(sink_name.to_string());
        Ok(())
    }

    pub async fn stop(&mut self) {
        if let Some(mut child) = self.child.take() {
            terminate_worker_child(&mut child).await;
        }
        if let Some(serial) = self.serial.take() {
            cleanup_remote_audio_server(&serial).await;
        }
        self.current_sink = None;
    }
}

pub fn spawn_audio_scrcpy(serial: &str, target_sink: &str) -> Result<Child> {
    let mut cmd = Command::new("scrcpy");
    cmd.args([
        "-s", serial,
        "--no-video",
        "--no-window",
        "--no-control",
        "--audio-source=output",
        "--audio-buffer=45",
        "--audio-output-buffer=45",
    ])
    .stdin(Stdio::null())
    .stdout(Stdio::null())
    .stderr(Stdio::null());

    cmd.env("SDL_VIDEODRIVER", "dummy");
    cmd.env("PULSE_SINK", target_sink);
    cmd.env("PIPEWIRE_NODE", target_sink);

    cmd.spawn().map_err(DaemonError::Io)
}

pub async fn terminate_worker_child(child: &mut Child) {
    if child.try_wait().ok().flatten().is_some() {
        return;
    }
    if let Some(pid) = child.id() {
        unsafe { libc::kill(pid as libc::pid_t, libc::SIGTERM); }
        if tokio::time::timeout(Duration::from_millis(1200), child.wait()).await.is_err() {
            let _ = child.kill().await;
            let _ = child.wait().await;
        }
    }
}

pub async fn cleanup_remote_audio_server(serial: &str) {
    let script = r#"for pid in $(pidof app_process); do if [ -f /proc/$pid/cmdline ]; then cmd=$(cat /proc/$pid/cmdline | tr '\0' ' '); case "$cmd" in *"video=false"*"control=false"*) case "$cmd" in *"audio=false"*) ;; *) kill -9 $pid 2>/dev/null ;; esac ;; esac; fi; done"#;
    let _ = tokio::time::timeout(
        Duration::from_millis(1500),
        Command::new("adb").args(["-s", serial, "shell", script]).output(),
    ).await;
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_worker_initial_state() {
        let mut worker = AudioWorker::new();
        assert!(!worker.is_active());
        assert_eq!(worker.current_sink(), None);
    }
}
