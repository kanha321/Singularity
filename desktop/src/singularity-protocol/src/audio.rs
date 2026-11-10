use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct AudioSinkInfo {
    pub name: String,
    pub description: String,
    pub nick: String,
    pub device_type: String,
    pub is_default: bool,
    pub volume: u32,
    pub mute: bool,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct AudioRouteCommand {
    pub action: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub target_sink: Option<String>,
    #[serde(default)]
    pub laptop_to_phone: bool,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub volume: Option<u32>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub mute: Option<bool>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub low_latency: Option<bool>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct ActiveAudioRouteInfo {
    pub output_device_name: String,
    pub output_device_label: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub source_app: Option<String>,
    pub source_origin: String,
    pub is_playing: bool,
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_audio_sink_info_roundtrip() {
        let sink = AudioSinkInfo {
            name: "alsa_output.hdmi-stereo".into(),
            description: "Odyssey G5".into(),
            nick: "Odyssey G5".into(),
            device_type: "hdmi".into(),
            is_default: true,
            volume: 100,
            mute: false,
        };
        let json = serde_json::to_string(&sink).unwrap();
        let parsed: AudioSinkInfo = serde_json::from_str(&json).unwrap();
        assert_eq!(sink, parsed);
    }

    #[test]
    fn test_audio_route_command_roundtrip() {
        let cmd = AudioRouteCommand {
            action: "set_route".into(),
            target_sink: Some("alsa_output.speaker".into()),
            laptop_to_phone: false,
            volume: Some(85),
            mute: Some(false),
            low_latency: Some(true),
        };
        let json = serde_json::to_string(&cmd).unwrap();
        let parsed: AudioRouteCommand = serde_json::from_str(&json).unwrap();
        assert_eq!(cmd, parsed);
        assert_eq!(parsed.low_latency, Some(true));
    }

    #[test]
    fn test_active_audio_route_info_roundtrip() {
        let route = ActiveAudioRouteInfo {
            output_device_name: "alsa_output.hdmi".into(),
            output_device_label: "Laptop: Odyssey G5".into(),
            source_app: Some("Thorium".into()),
            source_origin: "laptop".into(),
            is_playing: true,
        };
        let json = serde_json::to_string(&route).unwrap();
        let parsed: ActiveAudioRouteInfo = serde_json::from_str(&json).unwrap();
        assert_eq!(route, parsed);
    }
}
