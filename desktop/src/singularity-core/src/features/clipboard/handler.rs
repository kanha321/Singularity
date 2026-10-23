use std::hash::{Hash, Hasher};
use crate::bridge::DeviceBridge;
use crate::error::Result;
use crate::features::clipboard::config::ClipboardConfig;
use crate::features::clipboard::state::ClipboardState;

pub fn compute_hash(text: &str) -> u64 {
    let mut hasher = std::collections::hash_map::DefaultHasher::new();
    text.hash(&mut hasher);
    hasher.finish()
}

pub async fn handle_push_to_device(
    text: &str,
    config: &ClipboardConfig,
    state: &mut ClipboardState,
    device: &DeviceBridge,
) -> Result<()> {
    if !config.enabled || state.is_paused {
        return Ok(());
    }

    if text.len() > config.max_size_bytes {
        tracing::warn!(target: "clipboard_feature", len = text.len(), "Clip exceeds maximum size, dropping");
        return Ok(());
    }

    let hash = compute_hash(text);
    if state.is_echo(hash) {
        tracing::trace!(target: "clipboard_feature", "Dropping echo clip");
        return Ok(());
    }

    state.record_sync(hash);
    // Generic control packet dispatch via ShellBridge
    device.shell().send_control(text.as_bytes()).await?;
    tracing::info!(target: "clipboard_feature", len = text.len(), "Synchronized clipboard to device");
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn compute_hash_is_deterministic() {
        assert_eq!(compute_hash("hello"), compute_hash("hello"));
        assert_ne!(compute_hash("hello"), compute_hash("world"));
    }
}
