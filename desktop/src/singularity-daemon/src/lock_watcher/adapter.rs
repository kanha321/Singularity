use async_trait::async_trait;
use crate::error::Result;

/// Unified adapter interface for desktop lock screens and shell session managers.
#[async_trait]
pub trait LockerAdapter: Send + Sync {
    /// Friendly name of this adapter.
    fn name(&self) -> &'static str;

    /// Checks if this locker environment is currently active and usable.
    async fn is_available(&self) -> bool;

    /// Queries whether the screen is currently locked.
    async fn is_locked(&self) -> bool;

    /// Commands the screen to lock.
    async fn lock(&self) -> Result<()>;

    /// Commands the screen to unlock.
    async fn unlock(&self) -> Result<()>;
}

#[cfg(test)]
mod tests {
    use super::*;

    struct MockAdapter {
        locked: bool,
    }

    #[async_trait]
    impl LockerAdapter for MockAdapter {
        fn name(&self) -> &'static str { "mock" }
        async fn is_available(&self) -> bool { true }
        async fn is_locked(&self) -> bool { self.locked }
        async fn lock(&self) -> Result<()> { Ok(()) }
        async fn unlock(&self) -> Result<()> { Ok(()) }
    }

    #[tokio::test]
    async fn test_mock_adapter_contract() {
        let adapter = MockAdapter { locked: true };
        assert_eq!(adapter.name(), "mock");
        assert!(adapter.is_available().await);
        assert!(adapter.is_locked().await);
        assert!(adapter.lock().await.is_ok());
        assert!(adapter.unlock().await.is_ok());
    }
}

