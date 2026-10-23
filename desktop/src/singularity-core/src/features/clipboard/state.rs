use crate::features::clipboard::ring_buffer::EchoRingBuffer;

#[derive(Debug, Clone, Default)]
pub struct ClipboardState {
    pub is_paused: bool,
    pub clips_synced: u64,
    pub echo_buffer: EchoRingBuffer,
}

impl ClipboardState {
    pub fn new() -> Self {
        Self::default()
    }

    pub fn record_sync(&mut self, hash: u64) {
        self.clips_synced += 1;
        self.echo_buffer.record(hash);
    }

    pub fn is_echo(&self, hash: u64) -> bool {
        self.echo_buffer.is_echo(hash)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn state_detects_echo_hash_via_ring_buffer() {
        let mut state = ClipboardState::new();
        assert!(!state.is_echo(12345));

        state.record_sync(12345);
        assert!(state.is_echo(12345));
        assert!(!state.is_echo(54321));
        assert_eq!(state.clips_synced, 1);
    }
}
