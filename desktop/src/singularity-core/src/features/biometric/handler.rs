use crate::bridge::DeviceBridge;
use crate::error::{CoreError, Result};
use crate::features::biometric::state::BiometricState;

pub fn build_fingerprint_command(title: &str, reason: &str, timeout_secs: u32) -> String {
    format!(
        "timeout {0} /data/data/com.termux/files/usr/bin/termux-fingerprint -t '{1}' -d '{2}' -c 'Cancel'",
        timeout_secs, title, reason
    )
}

pub fn parse_fingerprint_output(output: &str) -> bool {
    output.contains("AUTH_RESULT_SUCCESS")
}

pub async fn handle_authenticate(
    title: &str,
    reason: &str,
    timeout_secs: u32,
    state: &mut BiometricState,
    device: &DeviceBridge,
) -> Result<bool> {
    let success = if let Ok(app) = device.app() {
        app.authenticate(reason, timeout_secs).await?
    } else if let Ok(root) = device.root() {
        let _ = device.shell().exec_command("input keyevent KEYCODE_WAKEUP").await;
        let cmd = build_fingerprint_command(title, reason, timeout_secs);
        let output = root.exec_su(&cmd).await?;
        parse_fingerprint_output(&output)
    } else {
        return Err(CoreError::BridgeUnavailable(
            "AppBridge or RootBridge required for biometric authentication",
        ));
    };

    state.record_result(success);
    Ok(success)
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::sync::Arc;
    use crate::bridge::{AppBridge, DeviceInfo, RootBridge, ShellBridge};

    #[test]
    fn build_fingerprint_command_formats_correctly() {
        let cmd = build_fingerprint_command("Security", "Sudo auth", 10);
        assert_eq!(
            cmd,
            "timeout 10 /data/data/com.termux/files/usr/bin/termux-fingerprint -t 'Security' -d 'Sudo auth' -c 'Cancel'"
        );
    }

    #[test]
    fn parse_fingerprint_output_detects_tokens() {
        assert!(parse_fingerprint_output(r#"{"auth_result":"AUTH_RESULT_SUCCESS"}"#));
        assert!(!parse_fingerprint_output(r#"{"auth_result":"AUTH_RESULT_FAILURE"}"#));
    }

    #[tokio::test]
    async fn handle_authenticate_supports_app_and_root_bridges() {
        let mut state = BiometricState::new();
        let info = DeviceInfo {
            serial: "test".into(),
            model: "test".into(),
            android_version: "16".into(),
        };

        // Neither bridge -> error
        let no_bridge = DeviceBridge::unrooted(info.clone(), Arc::new(ShellBridge::new()), None);
        assert!(handle_authenticate("T", "R", 5, &mut state, &no_bridge).await.is_err());

        // AppBridge available -> succeeds
        let app_device = DeviceBridge::unrooted(
            info.clone(),
            Arc::new(ShellBridge::new()),
            Some(Arc::new(AppBridge::new())),
        );
        assert!(handle_authenticate("T", "R", 5, &mut state, &app_device).await.unwrap());

        // RootBridge available -> succeeds
        let root_device = DeviceBridge::new(
            info,
            Some(Arc::new(RootBridge::new())),
            Arc::new(ShellBridge::new()),
            None,
        );
        assert!(handle_authenticate("T", "R", 5, &mut state, &root_device).await.is_ok());
    }
}
