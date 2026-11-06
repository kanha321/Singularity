use std::process::Command;
use crate::error::{DaemonError, Result};

pub const DEFAULT_MIC_SINK_NAME: &str = "singularity_mic_sink";
pub const DEFAULT_MIC_SOURCE_NAME: &str = "singularity_mic";

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct VirtualMicHandles {
    pub sink_id: u32,
    pub source_id: u32,
}

pub fn parse_module_id(output: &str) -> Option<u32> {
    output.trim().lines().next()?.trim().parse::<u32>().ok()
}

/// Setup a virtual PipeWire microphone using PulseAudio modules.
pub fn setup_virtual_microphone() -> Result<VirtualMicHandles> {
    let sink_out = Command::new("pactl")
        .args([
            "load-module", "module-null-sink",
            &format!("sink_name={DEFAULT_MIC_SINK_NAME}"),
            "sink_properties=device.description=\"Singularity_Mic_Backend\"",
        ])
        .output()
        .map_err(|e| DaemonError::Io(e))?;

    let sink_id = parse_module_id(&String::from_utf8_lossy(&sink_out.stdout))
        .ok_or_else(|| DaemonError::Storage("Failed to create PipeWire mic sink".into()))?;

    let src_out = Command::new("pactl")
        .args([
            "load-module", "module-remap-source",
            &format!("master={DEFAULT_MIC_SINK_NAME}.monitor"),
            &format!("source_name={DEFAULT_MIC_SOURCE_NAME}"),
            "source_properties=device.description=\"Singularity_Phone_Microphone\"",
        ])
        .output()
        .map_err(|e| DaemonError::Io(e))?;

    let source_id = match parse_module_id(&String::from_utf8_lossy(&src_out.stdout)) {
        Some(id) => id,
        None => {
            let _ = Command::new("pactl").args(["unload-module", &sink_id.to_string()]).output();
            return Err(DaemonError::Storage("Failed to create PipeWire mic source".into()));
        }
    };

    Ok(VirtualMicHandles { sink_id, source_id })
}

/// Teardown the virtual PipeWire microphone.
pub fn teardown_virtual_microphone(handles: &VirtualMicHandles) -> Result<()> {
    let _ = Command::new("pactl")
        .args(["unload-module", &handles.source_id.to_string()])
        .output();
    let _ = Command::new("pactl")
        .args(["unload-module", &handles.sink_id.to_string()])
        .output();
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parse_module_id_success() {
        assert_eq!(parse_module_id("536870916\n"), Some(536870916));
        assert_eq!(parse_module_id("  42  "), Some(42));
    }

    #[test]
    fn parse_module_id_invalid() {
        assert_eq!(parse_module_id(""), None);
        assert_eq!(parse_module_id("Failure: Module initialization failed"), None);
    }
}

