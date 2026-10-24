use tokio::process::Command;

pub async fn query_pc_screen_locked() -> bool {
    let out = Command::new("dms").args(["ipc", "call", "lock", "isLocked"]).output().await;
    match out {
        Ok(o) => String::from_utf8_lossy(&o.stdout).trim() == "true",
        Err(_) => {
            let bus = Command::new("busctl")
                .args(["--user", "call", "org.freedesktop.ScreenSaver", "/org/freedesktop/ScreenSaver", "org.freedesktop.ScreenSaver", "GetActive"])
                .output().await;
            bus.map(|o| String::from_utf8_lossy(&o.stdout).contains("true")).unwrap_or(false)
        }
    }
}

pub async fn query_pc_logged_in() -> bool {
    let out = Command::new("loginctl").args(["list-sessions", "--no-legend"]).output().await;
    out.map(|o| {
        String::from_utf8_lossy(&o.stdout).lines().any(|l| {
            let p: Vec<&str> = l.split_whitespace().collect();
            p.len() >= 6 && p.contains(&"user") && p.iter().any(|s| s.starts_with("seat"))
        })
    }).unwrap_or(false)
}

pub async fn trigger_pc_unlock() {
    tracing::info!(target: "lock_watcher", "Triggering PC screen unlock via DMS");
    let _ = Command::new("dms").args(["ipc", "call", "lock", "unlock"]).status().await;
}

pub async fn trigger_pc_lock() {
    tracing::info!(target: "lock_watcher", "Triggering PC screen lock via DMS & loginctl");
    let _ = Command::new("dms").args(["ipc", "call", "lock", "lock"]).status().await;
    let _ = Command::new("loginctl").args(["lock-session"]).status().await;
}

pub async fn trigger_pc_logout() {
    tracing::info!(target: "lock_watcher", "Triggering PC session logout via loginctl");
    if let Ok(out) = Command::new("loginctl").args(["list-sessions", "--no-legend"]).output().await {
        for line in String::from_utf8_lossy(&out.stdout).lines() {
            let parts: Vec<&str> = line.split_whitespace().collect();
            if parts.len() >= 6 && parts.contains(&"user") && parts.iter().any(|s| s.starts_with("seat")) {
                let _ = Command::new("loginctl").args(["terminate-session", parts[0]]).status().await;
            }
        }
    }
}

pub async fn trigger_pc_reboot() {
    tracing::info!(target: "lock_watcher", "Triggering PC reboot via systemctl");
    let _ = Command::new("systemctl").arg("reboot").status().await;
}

pub async fn trigger_pc_shutdown() {
    tracing::info!(target: "lock_watcher", "Triggering PC poweroff via systemctl");
    let _ = Command::new("systemctl").arg("poweroff").status().await;
}

pub async fn trigger_pc_suspend() {
    tracing::info!(target: "lock_watcher", "Locking screen and suspending PC");
    trigger_pc_lock().await;
    tokio::time::sleep(std::time::Duration::from_millis(300)).await;
    let _ = Command::new("systemctl").arg("suspend").status().await;
}

pub async fn trigger_pc_hibernate() {
    tracing::info!(target: "lock_watcher", "Locking screen and hibernating PC");
    trigger_pc_lock().await;
    tokio::time::sleep(std::time::Duration::from_millis(300)).await;
    let _ = Command::new("systemctl").arg("hibernate").status().await;
}
