use std::collections::HashMap;
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
    if let (Some(s), Some(e)) = (desc.find('['), desc.find(']')) {
        if e > s + 1 { return desc[s + 1..e].to_string(); }
    }
    if let Some(p) = props {
        if let Some(nick) = p.get("node.nick").and_then(|v| v.as_str()) {
            if !nick.is_empty() && !nick.starts_with("HDMI") { return nick.to_string(); }
        }
    }
    if desc.contains("DisplayPort 1") || desc.contains("HDMI 1") { return "DisplayPort 1".into(); }
    if desc.contains("DisplayPort 2") || desc.contains("HDMI 2") { return "DisplayPort 2".into(); }
    if desc.contains("DisplayPort 3") || desc.contains("HDMI 3") { return "DisplayPort 3".into(); }
    if desc.to_ascii_lowercase().contains("speaker") { return "Speaker".into(); }
    desc.to_string()
}

pub fn parse_pactl_sinks_json(
    json_str: &str,
    default_sink: Option<&str>,
    port_products: Option<&HashMap<String, String>>,
) -> Vec<AudioSinkInfo> {
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
        let desc = item.get("description").and_then(|v| v.as_str()).unwrap_or(&name).to_string();
        let active_port = item.get("active_port").and_then(|v| v.as_str());
        let nick = active_port
            .and_then(|ap| port_products.and_then(|pp| pp.get(ap)))
            .cloned()
            .unwrap_or_else(|| extract_friendly_nick(item.get("properties"), &desc));

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
    let sinks_raw = Command::new("pactl").args(["-f", "json", "list", "sinks"]).output().await;
    let json_str = sinks_raw.ok().map(|o| String::from_utf8_lossy(&o.stdout).to_string()).unwrap_or_default();

    let initial = parse_pactl_sinks_json(&json_str, def_sink.as_deref(), None);
    let names: Vec<String> = initial.iter().map(|s| s.name.clone()).collect();
    super::cards::heal_dormant_hdmi_sinks(&names).await;

    let port_products = super::cards::query_card_port_products().await;
    let sinks_final = Command::new("pactl").args(["-f", "json", "list", "sinks"]).output().await;
    let json_final = sinks_final.ok().map(|o| String::from_utf8_lossy(&o.stdout).to_string()).unwrap_or_default();
    parse_pactl_sinks_json(&json_final, def_sink.as_deref(), Some(&port_products))
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

        let dp = serde_json::json!({});
        assert_eq!(extract_friendly_nick(Some(&dp), "cAVS HDMI / DisplayPort 1 Output"), "DisplayPort 1");
        assert_eq!(extract_friendly_nick(None, "Laptop Internal Speaker"), "Speaker");
    }

    #[test]
    fn test_parse_pactl_sinks_json() {
        let sample = serde_json::json!([
            {
                "name": "alsa_output.pci-0000_01_00.1.hdmi-stereo",
                "description": "AD107 [Odyssey G5]",
                "active_port": "hdmi-output-0",
                "mute": false,
                "volume": { "front-left": { "value_percent": "100%" } }
            },
            {
                "name": "alsa_output.pci.speaker",
                "description": "Raptor Lake Speaker",
                "mute": true,
                "volume": { "front-left": { "value_percent": "86%" } }
            },
            {
                "name": "alsa_output.pci.hdmi_dp3",
                "description": "cAVS HDMI / DisplayPort 3 Output",
                "active_port": "[Out] HDMI3",
                "ports": [{ "name": "[Out] HDMI3", "availability": "not available" }]
            }
        ]);
        let mut port_products = HashMap::new();
        port_products.insert("hdmi-output-0".into(), "Odyssey G5".into());

        let sinks = parse_pactl_sinks_json(&sample.to_string(), Some("alsa_output.pci-0000_01_00.1.hdmi-stereo"), Some(&port_products));
        assert_eq!(sinks.len(), 3);
        assert_eq!(sinks[0].nick, "Odyssey G5");
        assert_eq!(sinks[0].device_type, "hdmi");
        assert!(sinks[0].is_default);

        assert_eq!(sinks[1].nick, "Speaker");
        assert_eq!(sinks[1].device_type, "speaker");

        assert_eq!(sinks[2].nick, "DisplayPort 3");
        assert_eq!(sinks[2].device_type, "hdmi");
    }
}
