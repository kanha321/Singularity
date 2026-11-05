use crate::bridge::ShellBridge;
use crate::error::Result;

pub fn build_launch_command(url: &str) -> String {
    let escaped_url = url.replace('\'', "'\\''");
    format!(
        "am start -a android.intent.action.VIEW -c android.intent.category.BROWSABLE -d '{}'",
        escaped_url
    )
}

pub async fn launch_url_on_device(shell: &ShellBridge, url: &str) -> Result<()> {
    let cmd = build_launch_command(url);
    shell.exec_command(&cmd).await?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_build_launch_command_escapes_single_quotes() {
        let cmd = build_launch_command("https://example.com/test?a='b'");
        assert_eq!(
            cmd,
            "am start -a android.intent.action.VIEW -c android.intent.category.BROWSABLE -d 'https://example.com/test?a='\\''b'\\'''"
        );
    }

    #[tokio::test]
    async fn test_launch_url_executes_on_shell_bridge() {
        let shell = ShellBridge::new();
        let res = launch_url_on_device(&shell, "https://example.com").await;
        assert!(res.is_ok());
    }
}

