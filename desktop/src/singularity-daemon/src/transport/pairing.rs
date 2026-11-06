use std::path::PathBuf;

fn paired_file_path() -> Option<PathBuf> {
    dirs::config_dir().map(|d| d.join("singularity").join("paired_devices.json"))
}

fn preferred_file_path() -> Option<PathBuf> {
    dirs::config_dir().map(|d| d.join("singularity").join("preferred_device"))
}

pub fn get_paired_devices() -> Vec<String> {
    if let Some(path) = paired_file_path() {
        if let Ok(content) = std::fs::read_to_string(path) {
            if let Ok(list) = serde_json::from_str::<Vec<String>>(&content) {
                return list;
            }
        }
    }
    Vec::new()
}

pub fn is_device_paired(serial: &str, model: Option<&str>) -> bool {
    let paired = get_paired_devices();
    if paired.is_empty() {
        return true; // Auto-pairing mode when no devices explicitly paired
    }
    paired.iter().any(|p| {
        p == serial || model.map_or(false, |m| m.eq_ignore_ascii_case(p))
    })
}

pub fn add_paired_device(ident: &str) -> bool {
    let mut paired = get_paired_devices();
    if !paired.contains(&ident.to_string()) {
        paired.push(ident.to_string());
        if let Some(path) = paired_file_path() {
            if let Some(parent) = path.parent() {
                let _ = std::fs::create_dir_all(parent);
            }
            let _ = std::fs::write(path, serde_json::to_string_pretty(&paired).unwrap_or_default());
        }
        return true;
    }
    false
}

pub fn remove_paired_device(ident: &str) -> bool {
    let mut paired = get_paired_devices();
    let initial_len = paired.len();
    paired.retain(|p| p != ident);
    if paired.len() != initial_len {
        if let Some(path) = paired_file_path() {
            let _ = std::fs::write(path, serde_json::to_string_pretty(&paired).unwrap_or_default());
        }
        return true;
    }
    false
}

pub fn get_preferred_device() -> Option<String> {
    if let Some(path) = preferred_file_path() {
        if let Ok(content) = std::fs::read_to_string(path) {
            let trimmed = content.trim().to_string();
            if !trimmed.is_empty() && trimmed != "auto" {
                return Some(trimmed);
            }
        }
    }
    None
}

pub fn set_preferred_device(serial: &str) {
    if let Some(path) = preferred_file_path() {
        if let Some(parent) = path.parent() {
            let _ = std::fs::create_dir_all(parent);
        }
        let _ = std::fs::write(path, serial.trim());
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn empty_paired_allows_any_device() {
        let paired = get_paired_devices();
        if paired.is_empty() {
            assert!(is_device_paired("dummy_serial", Some("dummy_model")));
        }
    }
}

