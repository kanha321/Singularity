use singularity_protocol::MediaState;

#[derive(Default)]
pub struct MediaArbitrator {
    desktop_paused_by_phone: bool,
    phone_paused_by_desktop: bool,
    last_desktop_status: String,
    last_phone_status: String,
}

#[derive(Debug, Default, PartialEq, Eq)]
pub struct ArbitrationDecision {
    pub pause_desktop: Option<String>,
    pub resume_desktop: Option<String>,
    pub pause_phone: bool,
    pub resume_phone: bool,
}

impl MediaArbitrator {
    pub fn new() -> Self {
        Self::default()
    }

    pub fn decide(&mut self, desktop: Option<&MediaState>, phone: Option<&MediaState>) -> ArbitrationDecision {
        let mut decision = ArbitrationDecision::default();
        let cur_desktop_status = desktop.map(|d| d.status.as_str()).unwrap_or("Stopped");
        let cur_phone_status = phone.map(|p| p.status.as_str()).unwrap_or("Stopped");

        let desktop_playing = cur_desktop_status.eq_ignore_ascii_case("playing");
        let phone_playing = cur_phone_status.eq_ignore_ascii_case("playing");

        let phone_just_started = phone_playing && !self.last_phone_status.eq_ignore_ascii_case("playing");
        let desktop_just_started = desktop_playing && !self.last_desktop_status.eq_ignore_ascii_case("playing");
        let phone_just_stopped = !phone_playing && self.last_phone_status.eq_ignore_ascii_case("playing");
        let desktop_just_stopped = !desktop_playing && self.last_desktop_status.eq_ignore_ascii_case("playing");

        if phone_just_started && desktop_playing {
            if let Some(d) = desktop {
                decision.pause_desktop = Some(d.player.clone());
                self.desktop_paused_by_phone = true;
                self.phone_paused_by_desktop = false;
            }
        } else if phone_just_stopped && self.desktop_paused_by_phone {
            if let Some(d) = desktop {
                if cur_desktop_status.eq_ignore_ascii_case("paused") {
                    decision.resume_desktop = Some(d.player.clone());
                }
            }
            self.desktop_paused_by_phone = false;
        }

        if desktop_just_started && phone_playing {
            decision.pause_phone = true;
            self.phone_paused_by_desktop = true;
            self.desktop_paused_by_phone = false;
        } else if desktop_just_stopped && self.phone_paused_by_desktop {
            if cur_phone_status.eq_ignore_ascii_case("paused") { decision.resume_phone = true; }
            self.phone_paused_by_desktop = false;
        }

        self.last_desktop_status = cur_desktop_status.to_string();
        self.last_phone_status = cur_phone_status.to_string();
        decision
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn fake_media(player: &str, status: &str) -> MediaState {
        MediaState {
            player: player.to_string(),
            title: "Test".to_string(),
            artist: "Test".to_string(),
            album: "".to_string(),
            art_url: None,
            art_base64: None,
            status: status.to_string(),
            position_ms: 0,
            duration_ms: 100000,
            can_play: true,
            can_pause: true,
            can_next: false,
            can_prev: false,
            can_seek: false,
        }
    }

    #[test]
    fn phone_starts_playing_pauses_desktop_and_resumes_when_phone_stops() {
        let mut arb = MediaArbitrator::new();
        let desk = fake_media("chromium", "Playing");
        let phone_paused = fake_media("youtube", "Paused");

        // 1. Initial tick with phone paused: no pause
        let d1 = arb.decide(Some(&desk), Some(&phone_paused));
        assert_eq!(d1, ArbitrationDecision::default());

        // 2. Phone starts playing: should pause desktop
        let phone_playing = fake_media("youtube", "Playing");
        let d2 = arb.decide(Some(&desk), Some(&phone_playing));
        assert_eq!(d2.pause_desktop, Some("chromium".to_string()));
        assert!(!d2.pause_phone);

        // 3. Desktop is now paused, phone continues playing: stable
        let desk_paused = fake_media("chromium", "Paused");
        let d3 = arb.decide(Some(&desk_paused), Some(&phone_playing));
        assert_eq!(d3, ArbitrationDecision::default());

        // 4. Phone stops/pauses: should resume desktop
        let d4 = arb.decide(Some(&desk_paused), Some(&phone_paused));
        assert_eq!(d4.resume_desktop, Some("chromium".to_string()));

        // 5. Subsequent ticks: no spurious resume
        let d5 = arb.decide(Some(&desk), Some(&phone_paused));
        assert_eq!(d5, ArbitrationDecision::default());
    }

    #[test]
    fn desktop_starts_playing_pauses_phone_and_resumes_when_desktop_stops() {
        let mut arb = MediaArbitrator::new();
        let phone = fake_media("youtube", "Playing");
        let desk_paused = fake_media("spotify", "Paused");

        let _ = arb.decide(Some(&desk_paused), Some(&phone));

        // Desktop starts playing: pauses phone
        let desk_playing = fake_media("spotify", "Playing");
        let d1 = arb.decide(Some(&desk_playing), Some(&phone));
        assert!(d1.pause_phone);
        assert_eq!(d1.pause_desktop, None);

        // Phone is paused, desktop continues: stable
        let phone_paused = fake_media("youtube", "Paused");
        let d2 = arb.decide(Some(&desk_playing), Some(&phone_paused));
        assert_eq!(d2, ArbitrationDecision::default());

        // Desktop pauses: resumes phone
        let d3 = arb.decide(Some(&desk_paused), Some(&phone_paused));
        assert!(d3.resume_phone);
    }
}
