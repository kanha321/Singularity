/// Embedded precompiled scrcpy-server bytecode (DEX/JAR) running on Android.
pub const SCRCPY_SERVER_BYTES: &[u8] = include_bytes!("../../../../assets/scrcpy-server.jar");

/// Version string matching the bundled scrcpy-server bytecode
pub const SCRCPY_SERVER_VERSION: &str = "5.0.1";

/// Canonical destination path on the Android device
pub const SCRCPY_SERVER_REMOTE_PATH: &str = "/data/local/tmp/scrcpy-server.jar";

/// Returns a reference to the embedded server binary bytes
pub fn server_bytes() -> &'static [u8] {
    SCRCPY_SERVER_BYTES
}

/// Returns the size in bytes of the embedded server archive
#[allow(dead_code)]
pub fn server_size() -> usize {
    SCRCPY_SERVER_BYTES.len()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn embedded_server_is_non_empty_and_valid_size() {
        let bytes = server_bytes();
        assert!(!bytes.is_empty(), "Embedded scrcpy-server.jar must not be empty");
        // Precompiled scrcpy-server 5.0.1 is ~733 KB (700K - 800K)
        assert!(bytes.len() > 700_000, "Size should be around 733KB, got {}", bytes.len());
        assert_eq!(server_size(), bytes.len());
        // Verify ZIP/JAR magic header (PK\x03\x04)
        assert_eq!(&bytes[0..4], &[0x50, 0x4B, 0x03, 0x04], "Must have valid ZIP/JAR magic header");
    }

    #[test]
    fn constants_are_consistent() {
        assert_eq!(SCRCPY_SERVER_VERSION, "5.0.1");
        assert_eq!(SCRCPY_SERVER_REMOTE_PATH, "/data/local/tmp/scrcpy-server.jar");
    }
}
