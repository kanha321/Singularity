use crate::media::{MediaArbitrator, PhoneMediaHub, PhoneMediaServer};
use singularity_protocol::{MediaAction, MediaState};

pub struct MediaSynchronizer {
    pub hub: PhoneMediaHub,
    pub server: Option<PhoneMediaServer>,
    pub arbitrator: MediaArbitrator,
}

impl MediaSynchronizer {
    pub async fn start() -> Self {
        let hub = PhoneMediaHub::new();
        let server = match PhoneMediaServer::start(hub.clone()).await {
            Ok(s) => {
                tracing::info!(target: "lock_watcher", "MPRIS PhoneMediaServer online (org.mpris.MediaPlayer2.singularity)");
                Some(s)
            }
            Err(e) => {
                tracing::warn!(target: "lock_watcher", error = %e, "MPRIS PhoneMediaServer unavailable");
                None
            }
        };
        Self {
            hub,
            server,
            arbitrator: MediaArbitrator::new(),
        }
    }

    pub fn pop_action(&self) -> Option<MediaAction> {
        self.hub.pop_action()
    }

    pub async fn sync_states(&mut self, desktop: Option<&MediaState>, phone: Option<&MediaState>) {
        if let Some(ref s) = self.server {
            s.update_and_notify(phone.cloned()).await;
        }
        let decision = self.arbitrator.decide(desktop, phone);
        if let Some(player) = decision.pause_desktop {
            crate::media::mpris::execute_media_action(&MediaAction {
                command: "pause".to_string(),
                player,
                position_ms: None,
            }).await;
        }
        if let Some(player) = decision.resume_desktop {
            crate::media::mpris::execute_media_action(&MediaAction {
                command: "play".to_string(),
                player,
                position_ms: None,
            }).await;
        }
        if decision.pause_phone {
            self.hub.send_command("pause").await;
        }
        if decision.resume_phone {
            self.hub.send_command("play").await;
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn test_media_synchronizer_creation() {
        let hub = PhoneMediaHub::new();
        let mut sync = MediaSynchronizer {
            hub: hub.clone(),
            server: None,
            arbitrator: MediaArbitrator::new(),
        };

        assert!(sync.pop_action().is_none());
        hub.send_command("next").await;
        let act = sync.pop_action().expect("action present");
        assert_eq!(act.command, "next");

        // Test sync_states without server
        sync.sync_states(None, None).await;
    }
}

