use singularity_protocol::{MediaAction, MediaState};
use tokio::process::Command;

pub async fn list_players() -> Vec<String> {
    let out = Command::new("busctl")
        .args(["--user", "--json=short", "call", "org.freedesktop.DBus", "/org/freedesktop/DBus", "org.freedesktop.DBus", "ListNames"])
        .output().await;
    let stdout = match out {
        Ok(o) if o.status.success() => o.stdout,
        _ => return Vec::new(),
    };
    let val: serde_json::Value = serde_json::from_slice(&stdout).unwrap_or_default();
    let mut players = Vec::new();
    if let Some(arr) = val.get("data").and_then(|d| d.get(0)).and_then(|d| d.as_array()) {
        for name in arr {
            if let Some(s) = name.as_str() {
                if s.starts_with("org.mpris.MediaPlayer2.") && !s.starts_with("org.mpris.MediaPlayer2.singularity") {
                    players.push(s.to_string());
                }
            }
        }
    }
    players
}

pub async fn get_active_media_state(host_ip: Option<&str>, port: u16) -> Option<MediaState> {
    let players = list_players().await;
    if players.is_empty() { return None; }
    let mut candidate = None;
    for player in players {
        if let Some(state) = query_player_state(&player, host_ip, port).await {
            if state.status == "Playing" {
                return Some(state);
            }
            if candidate.is_none() && state.status == "Paused" {
                candidate = Some(state);
            }
        }
    }
    candidate
}

async fn query_player_state(player: &str, host_ip: Option<&str>, port: u16) -> Option<MediaState> {
    let out = Command::new("busctl")
        .args(["--user", "--json=short", "call", player, "/org/mpris/MediaPlayer2", "org.freedesktop.DBus.Properties", "GetAll", "s", "org.mpris.MediaPlayer2.Player"])
        .output().await.ok()?;
    if !out.status.success() { return None; }
    let val: serde_json::Value = serde_json::from_slice(&out.stdout).ok()?;
    let dict = val.get("data")?.get(0)?;

    let status = dict.get("PlaybackStatus")?.get("data")?.as_str()?.to_string();
    let pos_us = dict.get("Position").and_then(|p| p.get("data")).and_then(|p| p.as_i64()).unwrap_or(0);
    let meta = dict.get("Metadata").and_then(|m| m.get("data"));

    let title = meta.and_then(|m| m.get("xesam:title")).and_then(|t| t.get("data")).and_then(|t| t.as_str()).unwrap_or("").to_string();
    if title.is_empty() && status == "Stopped" { return None; }

    let artist = meta.and_then(|m| m.get("xesam:artist")).and_then(|a| a.get("data"))
        .map(|a| {
            if let Some(arr) = a.as_array() {
                arr.iter().filter_map(|v| v.as_str()).collect::<Vec<_>>().join(", ")
            } else if let Some(s) = a.as_str() { s.to_string() } else { String::new() }
        }).unwrap_or_default();

    let album = meta.and_then(|m| m.get("xesam:album")).and_then(|a| a.get("data")).and_then(|a| a.as_str()).unwrap_or("").to_string();
    let len_us = meta.and_then(|m| m.get("mpris:length")).and_then(|l| l.get("data")).and_then(|l| l.as_i64()).unwrap_or(0);
    let raw_art = meta.and_then(|m| m.get("mpris:artUrl")).and_then(|u| u.get("data")).and_then(|u| u.as_str());

    let art_url = raw_art.map(|u| {
        if u.starts_with("file://") {
            let path = u.trim_start_matches("file://");
            let host = host_ip.unwrap_or("127.0.0.1");
            format!("http://{}:{}/api/media/art?path={}", host, port, url_encode(path))
        } else {
            u.to_string()
        }
    });

    Some(MediaState {
        player: player.to_string(),
        title, artist, album, art_url, art_base64: None, status,
        position_ms: (pos_us.max(0) / 1000) as u64,
        duration_ms: (len_us.max(0) / 1000) as u64,
        can_play: dict.get("CanPlay").and_then(|b| b.get("data")).and_then(|b| b.as_bool()).unwrap_or(true),
        can_pause: dict.get("CanPause").and_then(|b| b.get("data")).and_then(|b| b.as_bool()).unwrap_or(true),
        can_next: dict.get("CanGoNext").and_then(|b| b.get("data")).and_then(|b| b.as_bool()).unwrap_or(false),
        can_prev: dict.get("CanGoPrevious").and_then(|b| b.get("data")).and_then(|b| b.as_bool()).unwrap_or(false),
        can_seek: dict.get("CanSeek").and_then(|b| b.get("data")).and_then(|b| b.as_bool()).unwrap_or(false),
    })
}

pub async fn execute_media_action(action: &MediaAction) {
    let method = match action.command.as_str() {
        "play_pause" => "PlayPause",
        "play" => "Play",
        "pause" => "Pause",
        "next" => "Next",
        "previous" => "Previous",
        "seek" => {
            if let Some(pos_ms) = action.position_ms {
                seek_player(&action.player, pos_ms * 1000).await;
            }
            return;
        }
        _ => return,
    };
    let _ = Command::new("busctl")
        .args(["--user", "call", &action.player, "/org/mpris/MediaPlayer2", "org.mpris.MediaPlayer2.Player", method])
        .status().await;
}

async fn seek_player(player: &str, pos_us: u64) {
    let track_id = get_track_id(player).await.unwrap_or_else(|| "/org/mpris/MediaPlayer2/TrackList/NoTrack".to_string());
    let _ = Command::new("busctl")
        .args(["--user", "call", player, "/org/mpris/MediaPlayer2", "org.mpris.MediaPlayer2.Player", "SetPosition", "ox", &track_id, &pos_us.to_string()])
        .status().await;
}

async fn get_track_id(player: &str) -> Option<String> {
    let out = Command::new("busctl")
        .args(["--user", "--json=short", "call", player, "/org/mpris/MediaPlayer2", "org.freedesktop.DBus.Properties", "Get", "ss", "org.mpris.MediaPlayer2.Player", "Metadata"])
        .output().await.ok()?;
    let val: serde_json::Value = serde_json::from_slice(&out.stdout).ok()?;
    val.get("data")?.get(0)?.get("mpris:trackid")?.get("data")?.as_str().map(|s| s.to_string())
}

fn url_encode(s: &str) -> String {
    let mut out = String::new();
    for b in s.bytes() {
        if b.is_ascii_alphanumeric() || b == b'-' || b == b'_' || b == b'.' || b == b'/' {
            out.push(b as char);
        } else {
            out.push_str(&format!("%{:02X}", b));
        }
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_url_encode() {
        assert_eq!(url_encode("/tmp/hello world.png"), "/tmp/hello%20world.png");
    }
}
