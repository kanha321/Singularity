use singularity_protocol::AudioSinkInfo;
use tokio::process::Command;

pub fn classify_device_type(name: &str, desc: &str) -> &'static str {
    let n = name.to_ascii_lowercase();
    let d = desc.to_ascii_lowercase();
    if n.contains("hdmi") || d.contains("hdmi") || d.contains("displayport") { "hdmi" }
    else if n.contains("headphone") || d.contains("headphone") || d.contains("headset") { "headphones" }
    else if n.contains("speaker") || d.contains("speaker") { "speaker" }
    else if n.contains("bluez") || d.contains("bluetooth") { "bluetooth" }
    else { "generic" }
}

pub fn extract_friendly_nick(props: Option<&serde_json::Value>, desc: &str) -> String {
    if let Some(p) = props {
        if let Some(nick) = p.get("node.nick").and_then(|v| v.as_str()) {
            if !nick.is_empty() { return nick.to_string(); }
        }
    }
    if let (Some(s), Some(e)) = (desc.find('['), desc.find(']')) {
        if e > s + 1 { return desc[s + 1..e].to_string(); }
    }
    desc.to_string()
}

pub fn parse_pactl_sinks_json(json_str: &str, default_sink: Option<&str>) -> Vec<AudioSinkInfo> {
    let array: Vec<serde_json::Value> = match serde_json::from_str(json_str) {
        Ok(arr) => arr,
        Err(_) => return Vec::new(),
    };
    let mut sinks = Vec::new();
    for item in array {
        let name = match item.get("name").and_then(|v| v.as_str()) {
            Some(n) if !n.ends_with(".monitor") && !n.starts_with("singularity_") => n.to_string(),
            _ => continue,
        };
        if let Some(ports) = item.get("ports").and_then(|v| v.as_array()) {
            let active = item.get("active_port").and_then(|v| v.as_str());
            let unavailable = ports.iter().any(|p| {
                let m = active.map(|a| p.get("name").and_then(|v| v.as_str()) == Some(a)).unwrap_or(true);
                m && p.get("availability").and_then(|v| v.as_str()) == Some("not available")
            });
            if unavailable { continue; }
        }
        let desc = item.get("description").and_then(|v| v.as_str()).unwrap_or(&name).to_string();
        let nick = extract_friendly_nick(item.get("properties"), &desc);
        let device_type = classify_device_type(&name, &desc).to_string();
        let is_default = default_sink.map(|d| d == name).unwrap_or(false);
        let mute = item.get("mute").and_then(|v| v.as_bool()).unwrap_or(false);
        let volume = item.get("volume")
            .and_then(|v| v.get("front-left"))
            .and_then(|fl| fl.get("value_percent"))
            .and_then(|vp| vp.as_str())
            .and_then(|s| s.trim_end_matches('%').parse::<u32>().ok())
            .unwrap_or(100);
        sinks.push(AudioSinkInfo { name, description: desc, nick, device_type, is_default, volume, mute });
    }
    sinks
}

pub async fn query_laptop_sinks() -> Vec<AudioSinkInfo> {
    let def_out = Command::new("pactl").arg("get-default-sink").output().await;
    let def_sink = def_out.ok().and_then(|o| String::from_utf8(o.stdout).ok()).map(|s| s.trim().to_string());
    let sinks_out = match Command::new("pactl").args(["-f", "json", "list", "sinks"]).output().await {
        Ok(o) => o,
        Err(_) => return Vec::new(),
    };
    let json_str = String::from_utf8_lossy(&sinks_out.stdout);
    parse_pactl_sinks_json(&json_str, def_sink.as_deref())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_classify_device_type() {
        assert_eq!(classify_device_type("alsa_output.hdmi-stereo", "Digital Stereo"), "hdmi");
        assert_eq!(classify_device_type("alsa_output.speaker", "Internal Speakers"), "speaker");
        assert_eq!(classify_device_type("alsa_output.usb_headphone", "Headphones"), "headphones");
        assert_eq!(classify_device_type("bluez_output.earbuds", "Sony WF-1000XM4"), "bluetooth");
        assert_eq!(classify_device_type("alsa_output.pci", "Generic Audio Device"), "generic");
    }

    #[test]
    fn test_extract_friendly_nick() {
        let props = serde_json::json!({ "node.nick": "Odyssey G5" });
        assert_eq!(extract_friendly_nick(Some(&props), "AD107 High Definition [Odyssey G5]"), "Odyssey G5");

        let no_nick = serde_json::json!({});
        assert_eq!(extract_friendly_nick(Some(&no_nick), "AD107 High Definition [Odyssey G5]"), "Odyssey G5");
        assert_eq!(extract_friendly_nick(None, "Laptop Internal Speaker"), "Laptop Internal Speaker");
    }

    #[test]
    fn test_parse_pactl_sinks_json() {
        let sample = serde_json::json!([
            {
                "name": "alsa_output.pci-0000_01_00.1.hdmi-stereo",
                "description": "AD107 [Odyssey G5]",
                "mute": false,
                "volume": { "front-left": { "value_percent": "100%" } },
                "properties": { "node.nick": "Odyssey G5" }
            },
            {
                "name": "alsa_output.pci.speaker",
                "description": "Raptor Lake Speaker",
                "mute": true,
                "volume": { "front-left": { "value_percent": "86%" } }
            },
            {
                "name": "alsa_output.pci.speaker.monitor",
                "description": "Monitor of Speaker"
            },
            {
                "name": "singularity_phone_sink",
                "description": "Internal Virtual Sink"
            },
            {
                "name": "alsa_output.pci.hdmi_unplugged",
                "description": "HDMI 3 Output",
                "active_port": "hdmi-out",
                "ports": [{ "name": "hdmi-out", "availability": "not available" }]
            }
        ]);
        let sinks = parse_pactl_sinks_json(&sample.to_string(), Some("alsa_output.pci-0000_01_00.1.hdmi-stereo"));
        assert_eq!(sinks.len(), 2);
        assert_eq!(sinks[0].nick, "Odyssey G5");
        assert_eq!(sinks[0].device_type, "hdmi");
        assert!(sinks[0].is_default);
        assert_eq!(sinks[0].volume, 100);
        assert!(!sinks[0].mute);

        assert_eq!(sinks[1].device_type, "speaker");
        assert!(!sinks[1].is_default);
        assert_eq!(sinks[1].volume, 86);
        assert!(sinks[1].mute);
    }
}
