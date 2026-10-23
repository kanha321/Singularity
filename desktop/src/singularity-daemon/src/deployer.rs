use std::io::Write;
use tempfile::NamedTempFile;
use crate::assets::{server_bytes, SCRCPY_SERVER_REMOTE_PATH, SCRCPY_SERVER_VERSION};
use crate::error::Result;

pub struct ServerDeployer;

impl ServerDeployer {
    /// Writes the embedded server bytecode into a local temporary file for deployment
    pub fn create_staging_file() -> Result<NamedTempFile> {
        let mut temp_file = NamedTempFile::new()?;
        temp_file.write_all(server_bytes())?;
        temp_file.flush()?;
        Ok(temp_file)
    }

    /// Constructs the headless command line used to launch scrcpy-server via app_process
    pub fn build_headless_args(tunnel_forward: bool) -> Vec<String> {
        vec![
            "/data/local/tmp".into(),
            "com.genymobile.scrcpy.Server".into(),
            SCRCPY_SERVER_VERSION.into(),
            "video=false".into(),
            "audio=false".into(),
            "control=true".into(),
            "clipboard_autosync=true".into(),
            "cleanup=false".into(),
            format!("tunnel_forward={}", tunnel_forward),
        ]
    }

    /// Builds the full ADB shell execution command string
    pub fn build_shell_command(tunnel_forward: bool) -> String {
        let args = Self::build_headless_args(tunnel_forward);
        format!(
            "CLASSPATH={} app_process {}",
            SCRCPY_SERVER_REMOTE_PATH,
            args.join(" ")
        )
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn staging_file_contains_exact_embedded_server_bytes() {
        let temp = ServerDeployer::create_staging_file().unwrap();
        let metadata = temp.as_file().metadata().unwrap();
        assert_eq!(metadata.len() as usize, server_bytes().len());
    }

    #[test]
    fn headless_args_include_zero_overhead_flags() {
        let args = ServerDeployer::build_headless_args(true);
        assert!(args.contains(&"video=false".to_string()));
        assert!(args.contains(&"audio=false".to_string()));
        assert!(args.contains(&"control=true".to_string()));
        assert!(args.contains(&"clipboard_autosync=true".to_string()));
        assert!(args.contains(&"tunnel_forward=true".to_string()));
    }

    #[test]
    fn shell_command_sets_classpath_and_app_process() {
        let cmd = ServerDeployer::build_shell_command(true);
        assert!(cmd.starts_with("CLASSPATH=/data/local/tmp/scrcpy-server.jar app_process"));
        assert!(cmd.contains("video=false"));
    }
}
