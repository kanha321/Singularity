use crate::bridge::DeviceBridge;
use crate::error::Result;
use crate::features::calling::state::CallingState;

pub async fn handle_accept_call(state: &mut CallingState, device: &DeviceBridge) -> Result<()> {
    tracing::info!(target: "calling_feature", "Accepting incoming call via telecom CLI");
    device.shell().exec_command("telecom accept-call").await?;
    state.set_active();
    Ok(())
}

pub async fn handle_end_call(state: &mut CallingState, device: &DeviceBridge) -> Result<()> {
    tracing::info!(target: "calling_feature", "Ending call via telecom CLI");
    device.shell().exec_command("telecom end-call").await?;
    state.set_idle();
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::sync::Arc;
    use crate::bridge::{DeviceInfo, ShellBridge};

    #[tokio::test]
    async fn accept_and_end_call_transition_state() {
        let mut state = CallingState::new();
        let info = DeviceInfo {
            serial: "test".into(),
            model: "test".into(),
            android_version: "16".into(),
        };
        let device = DeviceBridge::unrooted(info, Arc::new(ShellBridge::new()), None);

        assert!(handle_accept_call(&mut state, &device).await.is_ok());
        assert!(state.is_in_call);

        assert!(handle_end_call(&mut state, &device).await.is_ok());
        assert!(!state.is_in_call);
    }
}
