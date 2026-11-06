use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct MediaState {
    pub player: String,
    pub title: String,
    pub artist: String,
    pub album: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub art_url: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub art_base64: Option<String>,
    pub status: String,
    pub position_ms: u64,
    pub duration_ms: u64,
    pub can_play: bool,
    pub can_pause: bool,
    pub can_next: bool,
    pub can_prev: bool,
    pub can_seek: bool,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct MediaAction {
    pub player: String,
    pub command: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub position_ms: Option<u64>,
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_media_state_roundtrip() {
        let state = MediaState {
            player: "org.mpris.MediaPlayer2.spotify".into(),
            title: "Song Title".into(),
            artist: "Artist Name".into(),
            album: "Album Name".into(),
            art_url: Some("http://localhost:8920/api/media/art".into()),
            art_base64: Some("AQIDBA==".into()),
            status: "Playing".into(),
            position_ms: 30000,
            duration_ms: 180000,
            can_play: true,
            can_pause: true,
            can_next: true,
            can_prev: true,
            can_seek: true,
        };
        let json = serde_json::to_string(&state).unwrap();
        let deserialized: MediaState = serde_json::from_str(&json).unwrap();
        assert_eq!(state, deserialized);
    }

    #[test]
    fn test_media_action_roundtrip() {
        let action = MediaAction {
            player: "org.mpris.MediaPlayer2.chromium".into(),
            command: "seek".into(),
            position_ms: Some(45000),
        };
        let json = serde_json::to_string(&action).unwrap();
        let deserialized: MediaAction = serde_json::from_str(&json).unwrap();
        assert_eq!(action, deserialized);
    }
}

