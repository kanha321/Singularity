use std::path::PathBuf;

pub fn get_or_save_icon(package_name: &str, base64_data: Option<&str>) -> Option<String> {
    if package_name.is_empty() { return None; }
    let safe_pkg: String = package_name.chars()
        .map(|c| if c.is_alphanumeric() || c == '.' || c == '_' || c == '-' { c } else { '_' })
        .collect();

    let cache_dir = dirs::cache_dir()
        .unwrap_or_else(|| PathBuf::from("/tmp"))
        .join("singularity")
        .join("icons");

    let icon_path = cache_dir.join(format!("{}.png", safe_pkg));
    if icon_path.exists() {
        return Some(icon_path.to_string_lossy().to_string());
    }

    if let Some(b64) = base64_data {
        if let Some(bytes) = crate::media::hub::decode_base64(b64) {
            if std::fs::create_dir_all(&cache_dir).is_ok() && std::fs::write(&icon_path, bytes).is_ok() {
                return Some(icon_path.to_string_lossy().to_string());
            }
        }
    }
    None
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_icon_cache_lifecycle() {
        let pkg = "com.test.app.sample";
        let b64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==";
        let path_str = get_or_save_icon(pkg, Some(b64)).expect("icon saved");
        let path = std::path::Path::new(&path_str);
        assert!(path.exists());

        // Second call should return existing path without b64
        let path_str2 = get_or_save_icon(pkg, None).expect("icon retrieved from cache");
        assert_eq!(path_str, path_str2);

        let _ = std::fs::remove_file(path);
    }
}

