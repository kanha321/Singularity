use std::path::Path;
use std::process::Command;
use std::time::{Duration, Instant};

const SYSTEM_HELPER: &str = "/usr/lib/singularity/reload-v4l2loopback";

pub async fn auto_load_v4l2_module() -> bool {
    let has_helper = Path::new(SYSTEM_HELPER).exists();
    let mut cmd = Command::new("pkexec");
    if has_helper {
        cmd.arg(SYSTEM_HELPER);
    } else {
        cmd.args([
            "sh", "-c",
            "if grep -q '^v4l2loopback ' /proc/modules; then modprobe -r v4l2loopback || exit 1; fi; modprobe v4l2loopback video_nr=10 card_label=\"Singularity Camera\" exclusive_caps=1"
        ]);
    }

    let Ok(status) = cmd.status() else { return false; };
    if !status.success() { return false; }

    let deadline = Instant::now() + Duration::from_secs(3);
    while Instant::now() < deadline {
        if crate::camera::v4l2::resolve_v4l2_device("/dev/video10").is_some() {
            return true;
        }
        tokio::time::sleep(Duration::from_millis(100)).await;
    }
    false
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_system_helper_path() {
        assert_eq!(SYSTEM_HELPER, "/usr/lib/singularity/reload-v4l2loopback");
    }
}

