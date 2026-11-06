use super::hub::PhoneMediaHub;
use std::collections::HashMap;
use zbus::zvariant::{ObjectPath, Value};

pub struct PhoneRootInterface;

#[zbus::interface(name = "org.mpris.MediaPlayer2")]
impl PhoneRootInterface {
    #[zbus(property)]
    fn can_quit(&self) -> bool { false }
    #[zbus(property)]
    fn fullscreen(&self) -> bool { false }
    #[zbus(property)]
    fn can_set_fullscreen(&self) -> bool { false }
    #[zbus(property)]
    fn can_raise(&self) -> bool { false }
    #[zbus(property)]
    fn has_track_list(&self) -> bool { false }
    #[zbus(property)]
    fn identity(&self) -> &str { "Singularity (Phone)" }
    #[zbus(property)]
    fn supported_uri_schemes(&self) -> Vec<String> { vec![] }
    #[zbus(property)]
    fn supported_mime_types(&self) -> Vec<String> { vec![] }
    async fn raise(&self) {}
    async fn quit(&self) {}
}

pub struct PhonePlayerInterface {
    pub hub: PhoneMediaHub,
}

#[zbus::interface(name = "org.mpris.MediaPlayer2.Player")]
impl PhonePlayerInterface {
    async fn next(&self) { self.hub.send_command("next").await; }
    async fn previous(&self) { self.hub.send_command("previous").await; }
    async fn pause(&self) { self.hub.send_command("pause").await; }
    async fn play_pause(&self) { self.hub.send_command("play_pause").await; }
    async fn stop(&self) { self.hub.send_command("pause").await; }
    async fn play(&self) { self.hub.send_command("play").await; }

    async fn seek(&self, offset_us: i64) {
        if let Some(m) = self.hub.get_media().await {
            let cur_us = (m.position_ms * 1000) as i64;
            let target_ms = ((cur_us + offset_us).max(0) / 1000) as u64;
            self.hub.send_seek(target_ms).await;
        }
    }

    async fn set_position(&self, _track_id: ObjectPath<'_>, position_us: i64) {
        self.hub.send_seek((position_us.max(0) / 1000) as u64).await;
    }

    async fn open_uri(&self, _uri: String) {}

    #[zbus(property)]
    async fn playback_status(&self) -> String {
        match self.hub.get_media().await {
            Some(m) if m.status.eq_ignore_ascii_case("playing") => "Playing".to_string(),
            Some(m) if m.status.eq_ignore_ascii_case("paused") => "Paused".to_string(),
            _ => "Stopped".to_string(),
        }
    }

    #[zbus(property)]
    fn loop_status(&self) -> &str { "None" }
    #[zbus(property)]
    fn rate(&self) -> f64 { 1.0 }
    #[zbus(property)]
    fn shuffle(&self) -> bool { false }

    #[zbus(property)]
    async fn metadata(&self) -> HashMap<String, Value<'static>> {
        let mut map = HashMap::new();
        let track_id = ObjectPath::try_from("/org/mpris/MediaPlayer2/Track/1").unwrap_or_default();
        map.insert("mpris:trackid".to_string(), Value::from(track_id));
        if let Some(m) = self.hub.get_media().await {
            map.insert("xesam:title".to_string(), Value::from(m.title));
            map.insert("xesam:artist".to_string(), Value::from(vec![m.artist]));
            map.insert("xesam:album".to_string(), Value::from(m.album));
            map.insert("mpris:length".to_string(), Value::from((m.duration_ms * 1000) as i64));
            if let Some(art) = m.art_url {
                if !art.is_empty() {
                    map.insert("mpris:artUrl".to_string(), Value::from(art));
                }
            }
        }
        map
    }

    #[zbus(property)]
    fn volume(&self) -> f64 { 1.0 }

    #[zbus(property)]
    async fn position(&self) -> i64 {
        self.hub.get_media().await.map(|m| (m.position_ms * 1000) as i64).unwrap_or(0)
    }

    #[zbus(property)]
    fn minimum_rate(&self) -> f64 { 1.0 }
    #[zbus(property)]
    fn maximum_rate(&self) -> f64 { 1.0 }

    #[zbus(property)]
    async fn can_go_next(&self) -> bool { self.hub.get_media().await.map(|m| m.can_next).unwrap_or(false) }
    #[zbus(property)]
    async fn can_go_previous(&self) -> bool { self.hub.get_media().await.map(|m| m.can_prev).unwrap_or(false) }
    #[zbus(property)]
    async fn can_play(&self) -> bool { self.hub.get_media().await.map(|m| m.can_play).unwrap_or(false) }
    #[zbus(property)]
    async fn can_pause(&self) -> bool { self.hub.get_media().await.map(|m| m.can_pause).unwrap_or(false) }
    #[zbus(property)]
    async fn can_seek(&self) -> bool { self.hub.get_media().await.map(|m| m.can_seek).unwrap_or(false) }
    #[zbus(property)]
    fn can_control(&self) -> bool { true }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn test_player_interface_properties_stopped() {
        let hub = PhoneMediaHub::new();
        let player = PhonePlayerInterface { hub };
        assert_eq!(player.playback_status().await, "Stopped");
        assert!(!player.can_play().await);
        assert_eq!(player.position().await, 0);
    }
}

