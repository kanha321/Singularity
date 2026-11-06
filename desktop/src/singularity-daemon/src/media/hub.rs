use singularity_protocol::{MediaAction, MediaState};
use std::sync::Arc;
use tokio::sync::{mpsc, Mutex, RwLock};

#[derive(Clone)]
pub struct PhoneMediaHub {
    current: Arc<RwLock<Option<MediaState>>>,
    action_tx: mpsc::UnboundedSender<MediaAction>,
    action_rx: Arc<Mutex<mpsc::UnboundedReceiver<MediaAction>>>,
}

impl PhoneMediaHub {
    pub fn new() -> Self {
        let (action_tx, action_rx) = mpsc::unbounded_channel();
        Self {
            current: Arc::new(RwLock::new(None)),
            action_tx,
            action_rx: Arc::new(Mutex::new(action_rx)),
        }
    }

    pub async fn get_media(&self) -> Option<MediaState> {
        self.current.read().await.clone()
    }

    pub async fn update_media(&self, mut media: Option<MediaState>) {
        if let Some(ref mut m) = media {
            if let Some(ref b64) = m.art_base64 {
                if let Some(path) = save_art_cache(b64) {
                    m.art_url = Some(format!("file://{}", path));
                }
                m.art_base64 = None;
            }
        }
        *self.current.write().await = media;
    }

    pub async fn send_command(&self, command: &str) {
        let player = self.get_media().await.map(|m| m.player).unwrap_or_default();
        let _ = self.action_tx.send(MediaAction {
            command: command.to_string(),
            player,
            position_ms: None,
        });
    }

    pub async fn send_seek(&self, position_ms: u64) {
        let player = self.get_media().await.map(|m| m.player).unwrap_or_default();
        let _ = self.action_tx.send(MediaAction {
            command: "seek".to_string(),
            player,
            position_ms: Some(position_ms),
        });
    }

    pub fn pop_action(&self) -> Option<MediaAction> {
        let mut rx = self.action_rx.try_lock().ok()?;
        rx.try_recv().ok()
    }
}

pub fn save_art_cache(b64: &str) -> Option<String> {
    let bytes = decode_base64(b64)?;
    let dir = dirs::runtime_dir().unwrap_or_else(std::env::temp_dir);
    let path = dir.join("singularity_phone_art.jpg");
    std::fs::write(&path, bytes).ok()?;
    path.to_str().map(|s| s.to_string())
}

pub fn decode_base64(input: &str) -> Option<Vec<u8>> {
    let mut table = [255u8; 256];
    for (i, &b) in b"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".iter().enumerate() {
        table[b as usize] = i as u8;
    }
    let input = input.trim().trim_end_matches('=');
    let mut buf = 0u32;
    let mut bits = 0;
    let mut out = Vec::with_capacity(input.len() * 3 / 4);
    for &b in input.as_bytes() {
        if b.is_ascii_whitespace() { continue; }
        let val = table[b as usize];
        if val == 255 { return None; }
        buf = (buf << 6) | (val as u32);
        bits += 6;
        if bits >= 8 {
            bits -= 8;
            out.push((buf >> bits) as u8);
        }
    }
    Some(out)
}

impl Default for PhoneMediaHub {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn test_hub_lifecycle_and_actions() {
        let hub = PhoneMediaHub::new();
        let media = MediaState {
            player: "com.spotify.music".to_string(),
            title: "Song A".to_string(),
            artist: "Artist B".to_string(),
            album: "Album C".to_string(),
            art_url: None,
            art_base64: Some("AQIDBA==".to_string()),
            status: "Playing".to_string(),
            position_ms: 15000,
            duration_ms: 200000,
            can_play: true,
            can_pause: true,
            can_next: true,
            can_prev: false,
            can_seek: true,
        };

        hub.update_media(Some(media)).await;
        let current = hub.get_media().await.expect("media present");
        assert_eq!(current.title, "Song A");
        assert!(current.art_url.as_ref().unwrap().starts_with("file://"));
        assert!(current.art_base64.is_none());

        hub.send_command("play_pause").await;
        let act = hub.pop_action().expect("action present");
        assert_eq!(act.command, "play_pause");
    }

    #[test]
    fn test_base64_decode() {
        assert_eq!(decode_base64("AQIDBA=="), Some(vec![1, 2, 3, 4]));
        assert_eq!(decode_base64("aGVsbG8="), Some(b"hello".to_vec()));
    }
}
