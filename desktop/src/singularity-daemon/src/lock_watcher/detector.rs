use std::sync::Arc;
use super::adapter::LockerAdapter;
use super::adapters::{CaelestiaAdapter, DmsAdapter, HyprlockAdapter, LogindAdapter};

/// Resolves the most appropriate locker adapter based on optional configuration and live environment detection.
pub async fn resolve_adapter(configured: Option<&str>) -> Arc<dyn LockerAdapter> {
    if let Some(target) = configured {
        match target.to_lowercase().as_str() {
            "caelestia" => return Arc::new(CaelestiaAdapter),
            "dms" => return Arc::new(DmsAdapter),
            "hyprlock" => return Arc::new(HyprlockAdapter),
            "logind" | "universal" => return Arc::new(LogindAdapter),
            _ => {}
        }
    }

    // Default to the universal multi-signal adapter: dynamically inspects DMS IPC,
    // active screen locker binaries (hyprlock, swaylock, etc.), Wayland session-lock
    // layers, and systemd-logind LockedHint on every query.
    tracing::info!(target: "lock_detector", "Selected universal multi-signal locker adapter");
    Arc::new(LogindAdapter)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn test_explicit_configuration_resolution() {
        assert_eq!(resolve_adapter(Some("caelestia")).await.name(), "caelestia");
        assert_eq!(resolve_adapter(Some("dms")).await.name(), "dms");
        assert_eq!(resolve_adapter(Some("hyprlock")).await.name(), "hyprlock");
        assert_eq!(resolve_adapter(Some("logind")).await.name(), "logind");
    }

    #[tokio::test]
    async fn test_auto_fallback_resolves_valid_adapter() {
        let adapter = resolve_adapter(None).await;
        assert_eq!(adapter.name(), "logind");
    }
}
