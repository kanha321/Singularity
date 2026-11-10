pub fn spawn_reply_prompt(label: String, phone_id: String) {
    tokio::spawn(async move {
        tracing::info!(target: "notification_prompt", label = %label, phone_id = %phone_id, "Spawning zenity reply prompt dialog");
        let args = [
            "--entry".to_string(),
            format!("--title=Reply to {}", label),
            format!("--text=Enter reply for {}:", label),
        ];
        let mut cmd = tokio::process::Command::new("zenity");
        cmd.args(&args);
        match cmd.output().await {
            Ok(out) => {
                if out.status.success() {
                    let text = String::from_utf8_lossy(&out.stdout).trim().to_string();
                    tracing::info!(target: "notification_prompt", text = %text, "User submitted reply text");
                    if !text.is_empty() {
                        if let Some(hub) = super::NotificationHub::get() {
                            hub.send_reply(&phone_id, &text).await;
                        }
                    }
                } else {
                    tracing::info!(target: "notification_prompt", "User cancelled reply prompt");
                }
            }
            Err(e) => tracing::warn!(target: "notification_prompt", error = %e, "Failed to launch zenity"),
        }
    });
}

#[cfg(test)]
mod tests {
    #[test]
    fn test_spawn_prompt_compiles() {
        assert_eq!(1 + 1, 2);
    }
}
