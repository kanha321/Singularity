#![allow(dead_code)]

use crate::error::{DaemonError, Result};
use tokio::process::Command;

pub async fn set_sink_volume(sink_name: &str, volume_percent: u32) -> Result<()> {
    let vol_str = format!("{}%", volume_percent.min(150));
    let status = Command::new("pactl")
        .args(["set-sink-volume", sink_name, &vol_str])
        .status()
        .await
        .map_err(DaemonError::Io)?;
    if !status.success() {
        return Err(DaemonError::Storage(format!("Failed to set volume for {sink_name}")));
    }
    Ok(())
}

pub async fn set_sink_mute(sink_name: &str, mute: bool) -> Result<()> {
    let mute_str = if mute { "1" } else { "0" };
    let status = Command::new("pactl")
        .args(["set-sink-mute", sink_name, mute_str])
        .status()
        .await
        .map_err(DaemonError::Io)?;
    if !status.success() {
        return Err(DaemonError::Storage(format!("Failed to set mute for {sink_name}")));
    }
    Ok(())
}

pub async fn move_sink_input(input_id: u32, target_sink: &str) -> Result<()> {
    let status = Command::new("pactl")
        .args(["move-sink-input", &input_id.to_string(), target_sink])
        .status()
        .await
        .map_err(DaemonError::Io)?;
    if !status.success() {
        return Err(DaemonError::Storage(format!("Failed to move sink input {input_id} to {target_sink}")));
    }
    Ok(())
}

pub fn parse_scrcpy_sink_input_index(json_str: &str, pid: Option<u32>) -> Option<u32> {
    let array: Vec<serde_json::Value> = serde_json::from_str(json_str).ok()?;
    for item in array {
        let props = match item.get("properties") {
            Some(p) => p,
            None => continue,
        };
        let is_scrcpy = props.get("application.name").and_then(|v| v.as_str()).map(|n| n.eq_ignore_ascii_case("scrcpy")).unwrap_or(false);
        let pid_matches = pid.map(|p| {
            let p_str = p.to_string();
            match props.get("application.process.id") {
                Some(serde_json::Value::String(s)) => s == &p_str,
                Some(serde_json::Value::Number(n)) => n.as_u64() == Some(p as u64),
                _ => false,
            }
        }).unwrap_or(true);
        if is_scrcpy && pid_matches {
            return item.get("index").and_then(|v| v.as_u64()).map(|i| i as u32);
        }
    }
    None
}

pub async fn find_scrcpy_sink_input(pid: Option<u32>) -> Option<u32> {
    let out = Command::new("pactl").args(["-f", "json", "list", "sink-inputs"]).output().await.ok()?;
    let json_str = String::from_utf8_lossy(&out.stdout);
    parse_scrcpy_sink_input_index(&json_str, pid)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_volume_clamp_logic() {
        assert_eq!(200u32.min(150), 150);
        assert_eq!(80u32.min(150), 80);
    }

    #[test]
    fn test_parse_scrcpy_sink_input_index() {
        let sample = serde_json::json!([
            {
                "index": 42,
                "properties": {
                    "application.name": "firefox",
                    "application.process.id": "1234"
                }
            },
            {
                "index": 99,
                "properties": {
                    "application.name": "scrcpy",
                    "application.process.id": "5678"
                }
            }
        ]);
        assert_eq!(parse_scrcpy_sink_input_index(&sample.to_string(), Some(5678)), Some(99));
        assert_eq!(parse_scrcpy_sink_input_index(&sample.to_string(), Some(9999)), None);
        assert_eq!(parse_scrcpy_sink_input_index(&sample.to_string(), None), Some(99));

        let sample_num = serde_json::json!([{
            "index": 101,
            "properties": { "application.name": "scrcpy", "application.process.id": 7777 }
        }]);
        assert_eq!(parse_scrcpy_sink_input_index(&sample_num.to_string(), Some(7777)), Some(101));
    }
}
