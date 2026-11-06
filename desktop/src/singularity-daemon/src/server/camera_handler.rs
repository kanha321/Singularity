use std::sync::Arc;
use tokio::sync::RwLock;
use singularity_protocol::{CameraConfig, CameraLens, IpcResponse};
use crate::camera::CameraSupervisor;
use crate::transport::resolve_or_wait_device;

pub async fn handle_start_camera(
    active_device: &Arc<RwLock<String>>,
    camera: &CameraSupervisor,
    config: Option<CameraConfig>,
) -> IpcResponse {
    let adb_address = match resolve_or_wait_device(active_device, 3).await {
        Some(addr) => addr,
        None => return IpcResponse::Error("No Android device connected".into()),
    };

    match camera.start(&adb_address, config).await {
        Ok(status) => IpcResponse::CameraStatus(status),
        Err(e) => IpcResponse::Error(format!("Failed to start camera: {e}")),
    }
}

pub async fn handle_stop_camera(camera: &CameraSupervisor) -> IpcResponse {
    match camera.stop().await {
        Ok(_) => IpcResponse::Ok,
        Err(e) => IpcResponse::Error(format!("Failed to stop camera: {e}")),
    }
}

pub async fn handle_switch_lens(
    active_device: &Arc<RwLock<String>>,
    camera: &CameraSupervisor,
    lens: CameraLens,
) -> IpcResponse {
    let adb_address = match resolve_or_wait_device(active_device, 2).await {
        Some(addr) => addr,
        None => "unknown".to_string(),
    };

    match camera.switch_lens(&adb_address, lens).await {
        Ok(status) => IpcResponse::CameraStatus(status),
        Err(e) => IpcResponse::Error(format!("Failed to switch lens: {e}")),
    }
}

pub async fn handle_set_torch(
    active_device: &Arc<RwLock<String>>,
    camera: &CameraSupervisor,
    enabled: bool,
) -> IpcResponse {
    let _ = camera.set_torch(enabled).await;
    if let Some(serial) = resolve_or_wait_device(active_device, 1).await {
        let val = if enabled { "255" } else { "0" };
        let cmd = format!("echo {val} > /sys/class/leds/led:torch_0/brightness 2>/dev/null || true");
        let _ = tokio::process::Command::new("adb")
            .args(["-s", &serial, "shell", &format!("su -c '{cmd}'")])
            .output()
            .await;
    }
    IpcResponse::Ok
}

pub async fn handle_set_zoom(camera: &CameraSupervisor, zoom: f32) -> IpcResponse {
    match camera.set_zoom(zoom).await {
        Ok(_) => IpcResponse::Ok,
        Err(e) => IpcResponse::Error(format!("Failed to set zoom: {e}")),
    }
}

pub async fn handle_get_status(camera: &CameraSupervisor) -> IpcResponse {
    let status = camera.status().await;
    IpcResponse::CameraStatus(status)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn test_camera_status_and_zoom() {
        let camera = CameraSupervisor::new();
        let resp = handle_get_status(&camera).await;
        match resp {
            IpcResponse::CameraStatus(s) => {
                assert!(!s.active);
                assert_eq!(s.lens, CameraLens::Main);
            }
            _ => panic!("Expected CameraStatus"),
        }

        let zoom_resp = handle_set_zoom(&camera, 2.0).await;
        assert!(matches!(zoom_resp, IpcResponse::Ok));
    }
}

