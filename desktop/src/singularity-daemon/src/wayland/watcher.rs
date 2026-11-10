use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::thread;
use std::time::Duration;
use tokio::sync::broadcast::Sender;
use wl_clipboard_rs::paste::Seat;
use wl_clipboard_rs::watch::{ClipboardEvent, ClipboardType, Watcher};

/// Backward-compatible alias for single MIME selection.
#[allow(dead_code)]
pub fn select_preferred_mime_type(mime_types: &[String]) -> Option<&str> {
    super::reader::candidate_mime_types(mime_types).first().copied()
}

/// Handle allowing caller to cleanly terminate the background Wayland watcher thread.
pub struct WaylandWatcherHandle {
    running: Arc<AtomicBool>,
}

impl WaylandWatcherHandle {
    #[allow(dead_code)]
    pub fn stop(&self) {
        self.running.store(false, Ordering::SeqCst);
    }
}

/// Spawns a dedicated thread watching for native Wayland selection changes.
pub fn start_wayland_watcher(tx: Sender<String>) -> crate::error::Result<WaylandWatcherHandle> {
    let running = Arc::new(AtomicBool::new(true));
    let running_thread = Arc::clone(&running);

    thread::Builder::new()
        .name("wayland-clipboard-watcher".into())
        .spawn(move || {
            tracing::info!(target: "wayland_watcher", "Wayland clipboard watcher thread started");
            while running_thread.load(Ordering::SeqCst) {
                super::ensure_wayland_env();
                let mut watcher = match Watcher::new(ClipboardType::Regular, Seat::Unspecified) {
                    Ok(w) => {
                        tracing::info!(target: "wayland_watcher", "Connected to Wayland clipboard watcher");
                        w
                    }
                    Err(e) => {
                        tracing::debug!(target: "wayland_watcher", error = %e, "Wayland compositor not ready; retrying in 2s");
                        thread::sleep(Duration::from_secs(2));
                        continue;
                    }
                };

                while running_thread.load(Ordering::SeqCst) {
                    match watcher.next_event() {
                        Ok(Some(ClipboardEvent::Changed { mime_types, mut offer, .. })) => {
                            if let Some(text) = super::reader::extract_text_from_offer(&mut offer, &mime_types) {
                                if !text.is_empty() {
                                    tracing::debug!(target: "wayland_watcher", len = text.len(), "Forwarding clipboard payload");
                                    let _ = tx.send(text);
                                }
                            }
                        }
                        Ok(Some(ClipboardEvent::Cleared { .. })) => {
                            tracing::trace!(target: "wayland_watcher", "Clipboard cleared event ignored");
                        }
                        Ok(None) => {
                            tracing::info!(target: "wayland_watcher", "Watcher cancelled gracefully");
                            return;
                        }
                        Err(e) => {
                            tracing::warn!(target: "wayland_watcher", error = %e, "Wayland clipboard event stream disconnected; reconnecting");
                            thread::sleep(Duration::from_millis(1000));
                            break;
                        }
                    }
                }
            }
            tracing::info!(target: "wayland_watcher", "Wayland clipboard watcher thread terminated");
        })?;

    Ok(WaylandWatcherHandle { running })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn select_preferred_mime_type_prefers_utf8_charset() {
        let types = vec![
            "text/html".to_string(),
            "text/plain;charset=utf-8".to_string(),
            "text/plain".to_string(),
        ];
        assert_eq!(select_preferred_mime_type(&types), Some("text/plain;charset=utf-8"));
    }

    #[test]
    fn select_preferred_mime_type_falls_back_to_text_plain() {
        let types = vec!["image/png".to_string(), "text/plain".to_string()];
        assert_eq!(select_preferred_mime_type(&types), Some("text/plain"));
    }

    #[test]
    fn select_preferred_mime_type_falls_back_to_utf8_string() {
        let types = vec!["application/octet-stream".to_string(), "UTF8_STRING".to_string()];
        assert_eq!(select_preferred_mime_type(&types), Some("UTF8_STRING"));
    }

    #[test]
    fn select_preferred_mime_type_returns_none_for_non_text() {
        let types = vec!["image/png".to_string(), "application/json".to_string()];
        assert_eq!(select_preferred_mime_type(&types), None);
    }
}
