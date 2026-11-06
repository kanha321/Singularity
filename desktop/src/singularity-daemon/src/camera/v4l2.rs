use std::path::Path;
use std::process::Command;

/// Check if the specified video device exists in /dev.
pub fn check_device_exists(device_path: &str) -> bool {
    Path::new(device_path).exists()
}

/// Configure v4l2loopback flags (keep_format, sustain_framerate) to prevent drops during transitions.
pub fn ensure_v4l2_sustained(device_path: &str) {
    if check_device_exists(device_path) {
        let _ = Command::new("v4l2-ctl")
            .args(["-d", device_path, "-c", "keep_format=1", "-c", "sustain_framerate=1"])
            .output();
    }
}

/// Parse PIDs from `fuser` command stdout.
pub fn parse_fuser_pids(output: &str) -> Vec<u32> {
    let tokens = match output.split_once(':') {
        Some((_, rest)) => rest,
        None => output,
    };
    tokens
        .split_whitespace()
        .filter_map(|token| {
            let clean: String = token.chars().filter(|c| c.is_ascii_digit()).collect();
            clean.parse::<u32>().ok()
        })
        .collect()
}

/// Query how many processes currently have the V4L2 device open.
#[allow(dead_code)]
pub fn count_device_readers(device_path: &str) -> u32 {
    count_external_readers(device_path, &[])
}

/// Query how many external processes (excluding ignored PIDs) have the device open.
pub fn count_external_readers(device_path: &str, ignored_pids: &[u32]) -> u32 {
    if !check_device_exists(device_path) {
        return 0;
    }
    match Command::new("fuser").arg(device_path).output() {
        Ok(out) => {
            let pids = parse_fuser_pids(&String::from_utf8_lossy(&out.stdout));
            pids.into_iter().filter(|pid| !ignored_pids.contains(pid)).count() as u32
        }
        Err(_) => 0,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parse_fuser_pids_empty() {
        assert_eq!(parse_fuser_pids(""), Vec::<u32>::new());
        assert_eq!(parse_fuser_pids("   \n\t "), Vec::<u32>::new());
    }

    #[test]
    fn parse_fuser_pids_with_access_suffixes() {
        // fuser outputs PIDs often followed by access characters like 'm', 'e', 'f'
        let output = "/dev/video10: 12345 67890m 11111f";
        let pids = parse_fuser_pids(output);
        assert_eq!(pids, vec![12345, 67890, 11111]);
    }

    #[test]
    fn parse_pure_pid_tokens() {
        let output = "18452 19284";
        assert_eq!(parse_fuser_pids(output), vec![18452, 19284]);
    }

    #[test]
    fn check_device_exists_behavior() {
        assert!(check_device_exists("/dev/null"));
        assert!(!check_device_exists("/dev/non_existent_video_device_xyz"));
    }

    #[test]
    fn filter_ignored_pids_logic() {
        let pids = vec![1001, 1002, 1003];
        let ignored = [1002];
        let count = pids.into_iter().filter(|p| !ignored.contains(p)).count();
        assert_eq!(count, 2);
    }
}
