pub mod watcher;
pub mod writer;

use std::path::PathBuf;

#[allow(unused_imports)]
pub use watcher::{select_preferred_mime_type, start_wayland_watcher, WaylandWatcherHandle};
#[allow(unused_imports)]
pub use writer::WaylandClipboardWriter;

/// Ensures WAYLAND_DISPLAY and XDG_RUNTIME_DIR are set in the process environment.
/// If running under systemd or headless boot where WAYLAND_DISPLAY is not yet exported,
/// auto-detects the active Wayland socket (e.g. wayland-1, wayland-0) in XDG_RUNTIME_DIR.
pub fn ensure_wayland_env() {
    let runtime_dir = std::env::var("XDG_RUNTIME_DIR")
        .unwrap_or_else(|_| format!("/run/user/{}", unsafe { libc::getuid() }));
    std::env::set_var("XDG_RUNTIME_DIR", &runtime_dir);

    if let Ok(current) = std::env::var("WAYLAND_DISPLAY") {
        if !current.trim().is_empty() && PathBuf::from(&runtime_dir).join(&current).exists() {
            return;
        }
    }

    if let Ok(entries) = std::fs::read_dir(&runtime_dir) {
        let mut candidates: Vec<String> = entries
            .flatten()
            .filter_map(|e| {
                let name = e.file_name().into_string().ok()?;
                if name.starts_with("wayland-") && !name.ends_with(".lock") {
                    Some(name)
                } else {
                    None
                }
            })
            .collect();
        candidates.sort();
        if let Some(sock_name) = candidates.pop() {
            tracing::info!(target: "wayland", display = %sock_name, "Auto-detected active Wayland display socket");
            std::env::set_var("WAYLAND_DISPLAY", sock_name);
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_ensure_wayland_env_does_not_panic() {
        ensure_wayland_env();
    }
}
