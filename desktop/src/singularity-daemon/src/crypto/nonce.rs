use std::collections::HashMap;
use std::sync::Arc;
use std::time::{Duration, Instant};
use rand::RngCore;
use tokio::sync::RwLock;

const NONCE_LEN: usize = 32;
const DEFAULT_TTL_SECS: u64 = 15;

#[derive(Debug, Clone)]
pub struct PendingChallenge {
    pub nonce: [u8; NONCE_LEN],
    pub created_at: Instant,
    pub ttl: Duration,
    pub service: String,
    pub user: String,
}

#[derive(Clone, Default)]
pub struct NonceManager {
    challenges: Arc<RwLock<HashMap<String, PendingChallenge>>>,
}

impl NonceManager {
    pub fn new() -> Self {
        Self::default()
    }

    pub async fn create_challenge(&self, id: &str, service: &str, user: &str) -> [u8; NONCE_LEN] {
        let mut nonce = [0u8; NONCE_LEN];
        rand::thread_rng().fill_bytes(&mut nonce);

        let challenge = PendingChallenge {
            nonce,
            created_at: Instant::now(),
            ttl: Duration::from_secs(DEFAULT_TTL_SECS),
            service: service.to_string(),
            user: user.to_string(),
        };

        let mut lock = self.challenges.write().await;
        // Purge expired
        lock.retain(|_, v| v.created_at.elapsed() < v.ttl);
        lock.insert(id.to_string(), challenge);

        nonce
    }

    pub async fn take_challenge(&self, id: &str) -> Option<PendingChallenge> {
        let mut lock = self.challenges.write().await;
        if let Some(challenge) = lock.remove(id) {
            if challenge.created_at.elapsed() <= challenge.ttl {
                return Some(challenge);
            }
        }
        None
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn nonce_manager_creates_and_takes_fresh_challenge() {
        let mgr = NonceManager::new();
        let nonce = mgr.create_challenge("sess1", "sudo", "kanhaji").await;
        assert_eq!(nonce.len(), 32);

        let taken = mgr.take_challenge("sess1").await.expect("Expected challenge");
        assert_eq!(taken.nonce, nonce);
        assert_eq!(taken.service, "sudo");

        // Second take returns None (single-use)
        assert!(mgr.take_challenge("sess1").await.is_none());
    }
}

