use crate::error::{DaemonError, Result};
use tokio::process::Command;

pub fn parse_sink_name_to_index(sinks_json: &str, sink_name: &str) -> Option<u64> {
    let array: Vec<serde_json::Value> = serde_json::from_str(sinks_json).ok()?;
    for item in array {
        if item.get("name").and_then(|v| v.as_str()) == Some(sink_name) {
            return item.get("index").and_then(|v| v.as_u64());
        }
    }
    None
}

pub fn parse_sink_inputs_to_migrate(sink_inputs_json: &str, from_sink_idx: u64) -> Vec<u32> {
    let mut indices = Vec::new();
    let array: Vec<serde_json::Value> = match serde_json::from_str(sink_inputs_json) {
        Ok(a) => a,
        Err(_) => return indices,
    };
    for item in array {
        let sink_idx = item.get("sink").and_then(|v| v.as_u64());
        if sink_idx != Some(from_sink_idx) {
            continue;
        }
        let is_scrcpy = item
            .get("properties")
            .and_then(|p| p.get("application.name"))
            .and_then(|v| v.as_str())
            .map(|n| n.eq_ignore_ascii_case("scrcpy"))
            .unwrap_or(false);
        if !is_scrcpy {
            if let Some(idx) = item.get("index").and_then(|v| v.as_u64()) {
                indices.push(idx as u32);
            }
        }
    }
    indices
}

pub fn parse_active_desktop_stream(
    sinks_json: &str,
    sink_inputs_json: &str,
) -> Option<(String, String)> {
    let sinks: Vec<serde_json::Value> = serde_json::from_str(sinks_json).ok()?;
    let inputs: Vec<serde_json::Value> = serde_json::from_str(sink_inputs_json).ok()?;

    for item in inputs {
        if item.get("corked").and_then(|v| v.as_bool()).unwrap_or(true) {
            continue;
        }
        let props = match item.get("properties") {
            Some(p) => p,
            None => continue,
        };
        let app_name = props
            .get("application.name")
            .and_then(|v| v.as_str())
            .unwrap_or("Desktop App");
        if app_name.eq_ignore_ascii_case("scrcpy") {
            continue;
        }
        let sink_idx = item.get("sink").and_then(|v| v.as_u64())?;
        for s in &sinks {
            if s.get("index").and_then(|v| v.as_u64()) == Some(sink_idx) {
                let sink_name = s.get("name").and_then(|v| v.as_str())?;
                return Some((sink_name.to_string(), app_name.to_string()));
            }
        }
    }
    None
}

pub async fn migrate_sink_inputs(from_sink: &str, to_sink: &str) -> Result<usize> {
    let sinks_out = Command::new("pactl").args(["-f", "json", "list", "sinks"]).output().await.map_err(DaemonError::Io)?;
    let sinks_json = String::from_utf8_lossy(&sinks_out.stdout);
    let from_idx = match parse_sink_name_to_index(&sinks_json, from_sink) {
        Some(idx) => idx,
        None => return Ok(0),
    };

    let inputs_out = Command::new("pactl").args(["-f", "json", "list", "sink-inputs"]).output().await.map_err(DaemonError::Io)?;
    let inputs_json = String::from_utf8_lossy(&inputs_out.stdout);
    let indices = parse_sink_inputs_to_migrate(&inputs_json, from_idx);

    let mut moved = 0;
    for id in indices {
        let status = Command::new("pactl")
            .args(["move-sink-input", &id.to_string(), to_sink])
            .status()
            .await
            .map_err(DaemonError::Io)?;
        if status.success() {
            moved += 1;
        }
    }
    Ok(moved)
}

pub async fn get_default_hardware_sink() -> String {
    let out = Command::new("pactl").arg("get-default-sink").output().await;
    let def = out.ok().and_then(|o| String::from_utf8(o.stdout).ok())
        .map(|s| s.trim().to_string())
        .filter(|s| !s.is_empty() && s != "singularity_phone_sink");
    if let Some(d) = def {
        return d;
    }
    let sinks = crate::audio::sinks::query_laptop_sinks().await;
    sinks.first().map(|s| s.name.clone()).unwrap_or_default()
}

pub async fn query_active_desktop_stream() -> Option<(String, String)> {
    let s_out = Command::new("pactl").args(["-f", "json", "list", "sinks"]).output().await.ok()?;
    let in_out = Command::new("pactl").args(["-f", "json", "list", "sink-inputs"]).output().await.ok()?;
    parse_active_desktop_stream(
        &String::from_utf8_lossy(&s_out.stdout),
        &String::from_utf8_lossy(&in_out.stdout),
    )
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_parse_sink_name_and_migrate() {
        let sinks = r#"[{"index": 10, "name": "singularity_phone_sink"}, {"index": 20, "name": "hdmi"}]"#;
        assert_eq!(parse_sink_name_to_index(sinks, "singularity_phone_sink"), Some(10));
        assert_eq!(parse_sink_name_to_index(sinks, "unknown"), None);

        let inputs = r#"[
            {"index": 1, "sink": 10, "corked": false, "properties": {"application.name": "Thorium"}},
            {"index": 2, "sink": 10, "corked": false, "properties": {"application.name": "scrcpy"}},
            {"index": 3, "sink": 20, "corked": false, "properties": {"application.name": "Spotify"}}
        ]"#;
        let to_migrate = parse_sink_inputs_to_migrate(inputs, 10);
        assert_eq!(to_migrate, vec![1]);

        let active = parse_active_desktop_stream(sinks, inputs);
        assert_eq!(active, Some(("singularity_phone_sink".into(), "Thorium".into())));
    }
}
