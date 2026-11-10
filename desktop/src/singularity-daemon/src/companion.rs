use std::time::Duration;
use tokio::io::{AsyncBufReadExt, AsyncWriteExt, BufReader};
use tokio::net::TcpStream;
use tokio::process::Command;
use crate::assets;

pub async fn ensure_deployed(serial: &str) -> bool {
    let check = Command::new("adb")
        .args(["-s", serial, "shell", "ls", assets::COMPANION_REMOTE_PATH])
        .output().await;
    if check.map(|o| o.status.success()).unwrap_or(false) {
        return true;
    }
    let temp = match tempfile::NamedTempFile::new() {
        Ok(t) => t,
        Err(_) => return false,
    };
    if std::fs::write(temp.path(), assets::COMPANION_BYTES).is_err() {
        return false;
    }
    Command::new("adb")
        .args(["-s", serial, "push", temp.path().to_str().unwrap_or(""), assets::COMPANION_REMOTE_PATH])
        .status().await.map(|s| s.success()).unwrap_or(false)
}

pub async fn launch(serial: &str) -> bool {
    let cmd = format!("CLASSPATH={} nohup app_process / SingularityCompanion >/dev/null 2>&1 &", assets::COMPANION_REMOTE_PATH);
    Command::new("adb")
        .args(["-s", serial, "shell", &cmd])
        .status().await.map(|s| s.success()).unwrap_or(false)
}

pub async fn send_command(serial: &str, req: &str) -> Option<String> {
    let fwd = Command::new("adb")
        .args(["-s", serial, "forward", "tcp:0", &format!("tcp:{}", assets::COMPANION_PORT)])
        .output().await.ok()?;
    if !fwd.status.success() { return None; }
    let port_str = String::from_utf8_lossy(&fwd.stdout);
    let port = port_str.trim().lines().next()?.parse::<u16>().ok()?;

    let res = tokio::time::timeout(Duration::from_millis(800), async {
        let mut stream = TcpStream::connect(("127.0.0.1", port)).await.ok()?;
        stream.write_all(format!("{}\n", req).as_bytes()).await.ok()?;
        let mut reader = BufReader::new(stream);
        let mut line = String::new();
        reader.read_line(&mut line).await.ok()?;
        Some(line)
    }).await.ok().flatten();

    let _ = Command::new("adb").args(["-s", serial, "forward", "--remove", &format!("tcp:{}", port)]).status().await;
    res
}

pub async fn ping(serial: &str) -> bool {
    send_command(serial, "{\"action\":\"ping\"}").await.map(|r| r.contains("\"ok\":true")).unwrap_or(false)
}

pub async fn kill_audio(serial: &str) -> Option<String> {
    send_command(serial, "{\"action\":\"kill_audio\"}").await
}

pub async fn disconnect_cleanup(serial: &str, app_foreground: bool) -> Option<String> {
    let req = format!("{{\"action\":\"disconnect_cleanup\",\"app_foreground\":{}}}", app_foreground);
    send_command(serial, &req).await
}

pub async fn setup(serial: &str) {
    if !ensure_deployed(serial).await {
        tracing::warn!(target: "companion", "Failed to deploy companion DEX");
        return;
    }
    if ping(serial).await {
        tracing::info!(target: "companion", "Companion already active on device");
        return;
    }
    launch(serial).await;
    for _ in 0..5 {
        tokio::time::sleep(Duration::from_millis(150)).await;
        if ping(serial).await {
            tracing::info!(target: "companion", "Companion successfully started and verified");
            return;
        }
    }
    tracing::warn!(target: "companion", "Companion launched but ping timed out");
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    #[ignore]
    async fn test_companion_live_device() {
        assert!(ping("10.229.40.89:5555").await);
    }
}
