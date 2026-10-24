pub mod session;
pub mod stream;

pub use session::*;
pub use stream::*;

use std::sync::Arc;
use std::time::Duration;
use tokio::sync::RwLock;
use crate::auth_cache::SharedAuthCache;

pub fn start_lock_watcher(
    active_device: Arc<RwLock<String>>,
    auth_cache: SharedAuthCache,
) -> tokio::task::JoinHandle<()> {
    tokio::spawn(async move {
        let mut last_state = None;
        let mut client: Option<AppBridgeStream> = None;
        let mut last_addr = String::new();

        loop {
            let adb_address = active_device.read().await.clone();
            if adb_address == "auto" {
                client = None;
                tokio::time::sleep(Duration::from_millis(500)).await;
                continue;
            }
            if adb_address != last_addr {
                last_addr = adb_address.clone();
                client = None;
            }

            let current_locked = query_pc_screen_locked().await;
            let current_logged_in = query_pc_logged_in().await;
            let current = (current_locked, current_logged_in);

            if last_state != Some(current) {
                last_state = Some(current);
                tracing::info!(target: "lock_watcher", is_locked = current_locked, is_logged_in = current_logged_in, "PC session state changed");
            }

            if client.is_none() {
                client = AppBridgeStream::connect(&adb_address).await.ok();
            }

            if let Some(ref mut stream) = client {
                match stream.exchange(current_locked, current_logged_in, &auth_cache).await {
                    Ok(actions) => {
                        if actions.lock {
                            trigger_pc_lock().await;
                        }
                        if actions.logout {
                            trigger_pc_logout().await;
                        }
                        if let Some(ref power) = actions.power {
                            match power.as_str() {
                                "reboot" => trigger_pc_reboot().await,
                                "shutdown" => trigger_pc_shutdown().await,
                                "logout" => trigger_pc_logout().await,
                                "suspend" => trigger_pc_suspend().await,
                                "hibernate" => trigger_pc_hibernate().await,
                                other => tracing::warn!(target: "lock_watcher", "Unknown power action: {}", other),
                            }
                        }
                        if actions.unlock {
                            let cache = auth_cache.read().await;
                            let denied = cache.is_denied("unlock") || cache.is_snoozed();
                            drop(cache);
                            if denied {
                                tracing::warn!(target: "lock_watcher", "Ignored PC unlock request: unlock is denied or snoozed");
                            } else {
                                trigger_pc_unlock().await;
                            }
                        }
                    }
                    Err(e) => {
                        tracing::debug!(target: "lock_watcher", "AppBridge socket dropped: {}, will reconnect", e);
                        client = None;
                    }
                }
            }

            tokio::time::sleep(Duration::from_millis(500)).await;
        }
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parse_lock_json_matches_expected() {
        let json = serde_json::json!({
            "action": "lock_state", "locked": true, "logged_in": false, "isLoggedIn": false,
            "request_lock": true, "request_logout": false, "request_unlock": false,
            "request_power": "suspend"
        });
        assert_eq!(json["locked"], true);
        assert_eq!(json["request_lock"], true);
        assert_eq!(json["request_power"], "suspend");
    }

    #[test]
    fn default_lock_actions_has_none_power() {
        let actions = LockActions::default();
        assert!(!actions.unlock);
        assert!(!actions.lock);
        assert!(!actions.logout);
        assert_eq!(actions.power, None);
    }
}
