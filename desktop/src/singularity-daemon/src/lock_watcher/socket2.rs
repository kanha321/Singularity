use std::path::PathBuf;
use std::sync::Arc;
use tokio::io::{AsyncBufReadExt, BufReader};
use tokio::net::UnixStream;
use tokio::sync::Notify;

pub fn find_hyprland_socket2() -> Option<PathBuf> {
    let runtime_dir = std::env::var("XDG_RUNTIME_DIR")
        .unwrap_or_else(|_| format!("/run/user/{}", unsafe { libc::getuid() }));
    let hypr_dir = PathBuf::from(runtime_dir).join("hypr");

    if let Ok(entries) = std::fs::read_dir(&hypr_dir) {
        for entry in entries.flatten() {
            let sock = entry.path().join(".socket2.sock");
            if sock.exists() {
                return Some(sock);
            }
        }
    }
    None
}

/// Spawns a background listener on Hyprland's socket2 to trigger instant state refreshes.
pub fn start_socket2_listener(notify: Arc<Notify>) -> Option<tokio::task::JoinHandle<()>> {
    let sock_path = find_hyprland_socket2()?;
    tracing::info!(target: "hypr_socket2", path = %sock_path.display(), "Connected to Hyprland event socket");

    Some(tokio::spawn(async move {
        loop {
            match UnixStream::connect(&sock_path).await {
                Ok(stream) => {
                    let mut reader = BufReader::new(stream);
                    let mut line = String::new();
                    while let Ok(bytes) = reader.read_line(&mut line).await {
                        if bytes == 0 {
                            break;
                        }
                        let event = line.trim();
                        if event.contains("layer") || event.contains("lock") {
                            notify.notify_waiters();
                        }
                        line.clear();
                    }
                }
                Err(e) => {
                    tracing::debug!(target: "hypr_socket2", error = %e, "Socket2 disconnected, retrying");
                }
            }
            tokio::time::sleep(std::time::Duration::from_secs(2)).await;
        }
    }))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_find_hyprland_socket2_path_logic() {
        let sock = find_hyprland_socket2();
        // If running in Hyprland session, returns valid socket
        if std::env::var("WAYLAND_DISPLAY").is_ok() {
            assert!(sock.is_some());
        }
    }
}

