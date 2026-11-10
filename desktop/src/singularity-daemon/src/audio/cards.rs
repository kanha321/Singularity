use std::collections::HashMap;
use tokio::process::Command;

pub async fn query_card_port_products() -> HashMap<String, String> {
    let out = match Command::new("pactl").args(["-f", "json", "list", "cards"]).output().await {
        Ok(o) if o.status.success() => o.stdout,
        _ => return HashMap::new(),
    };
    parse_card_ports_json(&String::from_utf8_lossy(&out))
}

pub fn parse_card_ports_json(json_str: &str) -> HashMap<String, String> {
    let array: Vec<serde_json::Value> = match serde_json::from_str(json_str) {
        Ok(arr) => arr,
        Err(_) => return HashMap::new(),
    };
    let mut map = HashMap::new();
    for card in array {
        if let Some(ports) = card.get("ports").and_then(|v| v.as_object()) {
            for (p_name, p_val) in ports {
                if let Some(prod) = p_val.get("properties")
                    .and_then(|pr| pr.get("device.product.name"))
                    .and_then(|v| v.as_str())
                {
                    if !prod.is_empty() {
                        map.insert(p_name.clone(), prod.to_string());
                    }
                }
            }
        }
    }
    map
}

pub async fn heal_dormant_hdmi_sinks(existing_sink_names: &[String]) {
    let out = match Command::new("pactl").args(["-f", "json", "list", "cards"]).output().await {
        Ok(o) if o.status.success() => o.stdout,
        _ => return,
    };
    let array: Vec<serde_json::Value> = match serde_json::from_str(&String::from_utf8_lossy(&out)) {
        Ok(arr) => arr,
        Err(_) => return,
    };
    for card in array {
        let card_name = match card.get("name").and_then(|v| v.as_str()) {
            Some(n) => n,
            None => continue,
        };
        let mut has_available_hdmi = false;
        if let Some(ports) = card.get("ports").and_then(|v| v.as_object()) {
            for (_, p_val) in ports {
                let is_avail = p_val.get("availability").and_then(|v| v.as_str()) == Some("available");
                let is_hdmi = p_val.get("type").and_then(|v| v.as_str()) == Some("HDMI");
                if is_avail && is_hdmi {
                    has_available_hdmi = true;
                    break;
                }
            }
        }
        if has_available_hdmi {
            let card_suffix = card_name.trim_start_matches("alsa_card.");
            let has_active_sink = existing_sink_names.iter().any(|s| s.contains(card_suffix));
            if !has_active_sink {
                let _ = Command::new("pactl").args(["set-card-profile", card_name, "off"]).output().await;
                let _ = Command::new("pactl").args(["set-card-profile", card_name, "output:hdmi-stereo"]).output().await;
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_parse_card_ports_json() {
        let json = serde_json::json!([
            {
                "name": "alsa_card.pci-0000_01_00.1",
                "ports": {
                    "hdmi-output-0": {
                        "type": "HDMI",
                        "availability": "available",
                        "properties": { "device.product.name": "Odyssey G5" }
                    },
                    "hdmi-output-1": { "type": "HDMI", "availability": "not available" }
                }
            }
        ]);
        let map = parse_card_ports_json(&json.to_string());
        assert_eq!(map.get("hdmi-output-0").map(|s| s.as_str()), Some("Odyssey G5"));
        assert_eq!(map.get("hdmi-output-1"), None);
    }
}

