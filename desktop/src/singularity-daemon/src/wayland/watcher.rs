use std::io::Read;
use std::thread;
use tokio::sync::broadcast::Sender;
use wl_clipboard_rs::paste::Seat;
use wl_clipboard_rs::watch::{CancelHandle, ClipboardEvent, ClipboardType, Watcher};

/// Inspects available MIME types and returns the best candidate for plain text extraction.
pub fn select_preferred_mime_type(mime_types: &[String]) -> Option<&str> {
    const CANDIDATES: &[&str] = &[
        "text/plain;charset=utf-8",
        "text/plain",
        "UTF8_STRING",
        "TEXT",
        "STRING",
    ];

    for candidate in CANDIDATES {
        if let Some(found) = mime_types.iter().find(|m| m.as_str() == *candidate) {
            return Some(found.as_str());
        }
    }

    mime_types.iter().find(|m| m.starts_with("text/")).map(|m| m.as_str())
}

/// Handle allowing caller to cleanly terminate the background Wayland watcher thread.
pub struct WaylandWatcherHandle {
    _cancel_handle: CancelHandle,
}

impl WaylandWatcherHandle {
    #[allow(dead_code)]
    pub fn stop(&self) {
        self._cancel_handle.cancel();
    }
}

/// Spawns a dedicated thread watching for native Wayland selection changes.
pub fn start_wayland_watcher(tx: Sender<String>) -> crate::error::Result<WaylandWatcherHandle> {
    let mut watcher = Watcher::new(ClipboardType::Regular, Seat::Unspecified)?;
    let cancel_handle = watcher.cancel_handle();
    let handle = WaylandWatcherHandle {
        _cancel_handle: cancel_handle.clone(),
    };

    thread::Builder::new()
        .name("wayland-clipboard-watcher".into())
        .spawn(move || {
            tracing::info!(target: "wayland_watcher", "Wayland clipboard watcher thread started");
            loop {
                match watcher.next_event() {
                    Ok(Some(ClipboardEvent::Changed { mime_types, mut offer, .. })) => {
                        if let Some(mime) = select_preferred_mime_type(&mime_types) {
                            match offer.receive(mime) {
                                Ok(mut reader) => {
                                    let mut content = String::new();
                                    if let Err(e) = reader.read_to_string(&mut content) {
                                        tracing::warn!(target: "wayland_watcher", error = %e, "Failed reading text from offer pipe");
                                        continue;
                                    }
                                    if !content.is_empty() {
                                        let _ = tx.send(content);
                                    }
                                }
                                Err(e) => {
                                    tracing::debug!(target: "wayland_watcher", error = %e, mime = mime, "Failed to receive offer stream");
                                }
                            }
                        }
                    }
                    Ok(Some(ClipboardEvent::Cleared { .. })) => {
                        tracing::trace!(target: "wayland_watcher", "Clipboard cleared event ignored");
                    }
                    Ok(None) => {
                        tracing::info!(target: "wayland_watcher", "Watcher cancelled gracefully");
                        break;
                    }
                    Err(e) => {
                        tracing::error!(target: "wayland_watcher", error = %e, "Error waiting for Wayland clipboard event");
                        break;
                    }
                }
            }
            tracing::info!(target: "wayland_watcher", "Wayland clipboard watcher thread terminated");
        })?;

    Ok(handle)
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
