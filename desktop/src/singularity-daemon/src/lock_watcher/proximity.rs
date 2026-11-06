use std::time::{Duration, Instant};
use crate::lock_watcher::adapter::LockerAdapter;
use crate::lock_watcher::input_activity::InputActivityTracker;

pub struct ProximityLockManager {
    is_connected: bool,
    idle_inhibited: bool,
    disconnected_at: Option<Instant>,
    activity_tracker: InputActivityTracker,
    lock_delay: Duration,
}

impl ProximityLockManager {
    pub fn new(activity_tracker: InputActivityTracker) -> Self {
        Self::with_delay(activity_tracker, Duration::from_secs(15))
    }

    pub fn with_delay(activity_tracker: InputActivityTracker, lock_delay: Duration) -> Self {
        Self { is_connected: false, idle_inhibited: false, disconnected_at: None, activity_tracker, lock_delay }
    }

    pub async fn on_connected(&mut self, adapter: &dyn LockerAdapter) {
        if !self.idle_inhibited {
            if let Err(e) = adapter.inhibit_idle().await {
                tracing::warn!(target: "proximity", "Failed to inhibit idle: {}", e);
            } else {
                self.idle_inhibited = true;
                tracing::info!(target: "proximity", "Keep-awake enabled (idle inhibited)");
            }
        }
        self.is_connected = true;
        self.disconnected_at = None;
    }

    pub async fn on_disconnected(&mut self, adapter: &dyn LockerAdapter) {
        if self.is_connected {
            self.is_connected = false;
            if self.idle_inhibited {
                let _ = adapter.uninhibit_idle().await;
                self.idle_inhibited = false;
                tracing::info!(target: "proximity", "Keep-awake released (idle uninhibited)");
            }
            self.disconnected_at = Some(Instant::now());
            tracing::info!(target: "proximity", "Phone disconnected; started 15s inactivity countdown");
        }
    }

    pub async fn check_inactivity_lock(&mut self, adapter: &dyn LockerAdapter, is_locked: bool) {
        if is_locked {
            self.disconnected_at = None;
            return;
        }
        if let Some(disco_time) = self.disconnected_at {
            if disco_time.elapsed() >= self.lock_delay && self.activity_tracker.idle_duration() >= self.lock_delay {
                tracing::info!(target: "proximity", "15s inactivity elapsed while disconnected; locking screen");
                let _ = adapter.lock().await;
                self.disconnected_at = None;
            }
        }
    }

    #[cfg(test)]
    pub fn is_inhibited(&self) -> bool { self.idle_inhibited }
    #[cfg(test)]
    pub fn is_countdown_active(&self) -> bool { self.disconnected_at.is_some() }
}

#[cfg(test)]
mod tests {
    use super::*;
    use async_trait::async_trait;
    use std::sync::atomic::{AtomicBool, Ordering};
    use std::sync::Arc;
    use crate::error::Result;

    struct MockAdapter {
        locked: Arc<AtomicBool>,
        inhibited: Arc<AtomicBool>,
    }

    #[async_trait]
    impl LockerAdapter for MockAdapter {
        fn name(&self) -> &'static str { "mock" }
        async fn is_available(&self) -> bool { true }
        async fn is_locked(&self) -> bool { self.locked.load(Ordering::Relaxed) }
        async fn lock(&self) -> Result<()> { self.locked.store(true, Ordering::Relaxed); Ok(()) }
        async fn unlock(&self) -> Result<()> { self.locked.store(false, Ordering::Relaxed); Ok(()) }
        async fn inhibit_idle(&self) -> Result<()> { self.inhibited.store(true, Ordering::Relaxed); Ok(()) }
        async fn uninhibit_idle(&self) -> Result<()> { self.inhibited.store(false, Ordering::Relaxed); Ok(()) }
    }

    #[tokio::test]
    async fn test_proximity_lifecycle_and_user_activity() {
        let adapter = MockAdapter {
            locked: Arc::new(AtomicBool::new(false)),
            inhibited: Arc::new(AtomicBool::new(false)),
        };
        let tracker = InputActivityTracker::start();
        let mut mgr = ProximityLockManager::with_delay(tracker.clone(), Duration::from_millis(50));

        mgr.on_connected(&adapter).await;
        assert!(mgr.is_inhibited() && adapter.inhibited.load(Ordering::Relaxed));

        mgr.on_disconnected(&adapter).await;
        assert!(!mgr.is_inhibited() && !adapter.inhibited.load(Ordering::Relaxed));
        assert!(mgr.is_countdown_active());

        // Simulating activity before timeout
        tokio::time::sleep(Duration::from_millis(30)).await;
        tracker.record_activity();
        tokio::time::sleep(Duration::from_millis(30)).await;
        mgr.check_inactivity_lock(&adapter, false).await;
        assert!(!adapter.locked.load(Ordering::Relaxed));

        // Let full delay expire after activity
        tokio::time::sleep(Duration::from_millis(30)).await;
        mgr.check_inactivity_lock(&adapter, false).await;
        assert!(adapter.locked.load(Ordering::Relaxed));
        assert!(!mgr.is_countdown_active());
    }
}
