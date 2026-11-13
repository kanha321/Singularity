use std::sync::Mutex;
use std::sync::atomic::{AtomicBool, Ordering};
use tokio::io::AsyncWriteExt;
use tokio::net::TcpStream;

static PENDING_COMMAND: Mutex<Option<bool>> = Mutex::new(None);
static IS_RINGING: AtomicBool = AtomicBool::new(false);

pub fn set_ring_request(ring: bool) {
    if let Ok(mut cmd) = PENDING_COMMAND.lock() {
        *cmd = Some(ring);
    }
    if !ring {
        IS_RINGING.store(false, Ordering::SeqCst);
    }
}

pub fn pop_ring_request() -> Option<bool> {
    PENDING_COMMAND.lock().ok().and_then(|mut cmd| cmd.take())
}

pub fn update_ringing_state(ringing: bool) {
    IS_RINGING.store(ringing, Ordering::SeqCst);
}

pub fn is_ringing() -> bool {
    IS_RINGING.load(Ordering::SeqCst)
}

/// Attempts a fast direct TCP trigger on port 2828 if phone IP is reachable over local Wi-Fi.
pub async fn send_direct_tcp_ring(ip_or_addr: &str, ring: bool) {
    let ip = ip_or_addr.split(':').next().unwrap_or(ip_or_addr);
    if ip == "auto" || ip.is_empty() { return; }
    let target = format!("{}:2828", ip);
    if let Ok(mut stream) = tokio::time::timeout(std::time::Duration::from_millis(500), TcpStream::connect(&target)).await.unwrap_or(Err(std::io::Error::new(std::io::ErrorKind::TimedOut, "timeout"))) {
        let payload = serde_json::json!({
            "action": "find_device",
            "ring": ring
        });
        let mut msg = serde_json::to_string(&payload).unwrap_or_default();
        msg.push('\n');
        let _ = stream.write_all(msg.as_bytes()).await;
        let _ = stream.flush().await;
        tracing::info!(target: "find_device", target = %target, ring = ring, "Dispatched direct TCP find_device command");
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_ring_state_transitions() {
        set_ring_request(true);
        assert_eq!(pop_ring_request(), Some(true));
        assert_eq!(pop_ring_request(), None);
        update_ringing_state(true);
        assert!(is_ringing());
        update_ringing_state(false);
        assert!(!is_ringing());
    }
}
