use std::time::{Duration, Instant};

pub const DEFAULT_ECHO_CAPACITY: usize = 16;
pub const DEFAULT_ECHO_TTL: Duration = Duration::from_secs(5);

/// Sliding window ring buffer tracking recent clip hashes to prevent reflection loops.
#[derive(Debug, Clone)]
pub struct EchoRingBuffer {
    entries: [(u64, Option<Instant>); DEFAULT_ECHO_CAPACITY],
    cursor: usize,
    ttl: Duration,
}

impl EchoRingBuffer {
    pub fn new(ttl: Duration) -> Self {
        Self {
            entries: [(0, None); DEFAULT_ECHO_CAPACITY],
            cursor: 0,
            ttl,
        }
    }

    /// Records a new content hash with the current timestamp.
    pub fn record(&mut self, hash: u64) {
        self.entries[self.cursor] = (hash, Some(Instant::now()));
        self.cursor = (self.cursor + 1) % DEFAULT_ECHO_CAPACITY;
    }

    /// Checks if a hash exists in the buffer and was recorded within the TTL window.
    pub fn is_echo(&self, hash: u64) -> bool {
        let now = Instant::now();
        for &(entry_hash, timestamp) in &self.entries {
            if entry_hash == hash {
                if let Some(ts) = timestamp {
                    if now.duration_since(ts) <= self.ttl {
                        return true;
                    }
                }
            }
        }
        false
    }
}

impl Default for EchoRingBuffer {
    fn default() -> Self {
        Self::new(DEFAULT_ECHO_TTL)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn ring_buffer_detects_recent_hashes() {
        let mut buffer = EchoRingBuffer::new(Duration::from_secs(2));
        assert!(!buffer.is_echo(12345));

        buffer.record(12345);
        assert!(buffer.is_echo(12345));
        assert!(!buffer.is_echo(99999));
    }

    #[test]
    fn ring_buffer_wraps_around_correctly() {
        let mut buffer = EchoRingBuffer::new(Duration::from_secs(10));
        for i in 0..20 {
            buffer.record(i);
        }
        // First few items should have been overwritten by wrap-around
        assert!(!buffer.is_echo(0));
        assert!(!buffer.is_echo(1));
        assert!(!buffer.is_echo(2));
        assert!(!buffer.is_echo(3));
        // Recent items remain present
        assert!(buffer.is_echo(18));
        assert!(buffer.is_echo(19));
    }

    #[test]
    fn ring_buffer_expires_old_hashes() {
        let mut buffer = EchoRingBuffer::new(Duration::from_millis(50));
        buffer.record(42);
        assert!(buffer.is_echo(42));

        std::thread::sleep(Duration::from_millis(60));
        assert!(!buffer.is_echo(42));
    }
}
