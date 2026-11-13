use std::path::PathBuf;
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AuthorizedDevice {
    pub device_id: String,
    pub device_name: String,
    pub model: Option<String>,
    pub public_key: String,
    pub last_counter: u64,
    pub created_at: u64,
    pub revoked: bool,
}

fn auth_file_path() -> Option<PathBuf> {
    dirs::config_dir().map(|d| d.join("singularity").join("authorized_devices.json"))
}

fn paired_file_path() -> Option<PathBuf> {
    dirs::config_dir().map(|d| d.join("singularity").join("paired_devices.json"))
}

fn preferred_file_path() -> Option<PathBuf> {
    dirs::config_dir().map(|d| d.join("singularity").join("preferred_device"))
}

pub fn get_authorized_devices() -> Vec<AuthorizedDevice> {
    auth_file_path().and_then(|p| std::fs::read_to_string(p).ok())
        .and_then(|c| serde_json::from_str(&c).ok())
        .unwrap_or_default()
}

pub fn get_authorized_device(ident: &str) -> Option<AuthorizedDevice> {
    get_authorized_devices().into_iter().find(|d| !d.revoked && (d.device_id == ident || d.model.as_deref() == Some(ident)))
}

pub fn save_authorized_device(dev: AuthorizedDevice) -> bool {
    let mut list = get_authorized_devices();
    list.retain(|d| d.device_id != dev.device_id);
    list.push(dev);
    if let Some(path) = auth_file_path() {
        if let Some(parent) = path.parent() { let _ = std::fs::create_dir_all(parent); }
        return std::fs::write(path, serde_json::to_string_pretty(&list).unwrap_or_default()).is_ok();
    }
    false
}

pub fn update_counter(device_id: &str, counter: u64) -> bool {
    let mut list = get_authorized_devices();
    if let Some(dev) = list.iter_mut().find(|d| d.device_id == device_id) {
        dev.last_counter = counter;
        if let Some(path) = auth_file_path() {
            let _ = std::fs::write(path, serde_json::to_string_pretty(&list).unwrap_or_default());
            return true;
        }
    }
    false
}

pub fn get_paired_device_name(serial: &str) -> Option<String> {
    get_authorized_device(serial).map(|d| d.device_name)
}

pub fn is_device_paired(serial: &str, model: Option<&str>) -> bool {
    let authorized = get_authorized_devices();
    if !authorized.is_empty() {
        return authorized.iter().any(|d| !d.revoked && (d.device_id == serial || model.map_or(false, |m| m.eq_ignore_ascii_case(&d.device_id) || d.model.as_deref() == Some(m))));
    }
    let legacy = paired_file_path().and_then(|p| std::fs::read_to_string(p).ok())
        .and_then(|c| serde_json::from_str::<Vec<String>>(&c).ok()).unwrap_or_default();
    if legacy.is_empty() { return true; }
    legacy.iter().any(|p| p == serial || model.map_or(false, |m| m.eq_ignore_ascii_case(p)))
}

pub fn get_preferred_device() -> Option<String> {
    preferred_file_path().and_then(|p| std::fs::read_to_string(p).ok())
        .map(|c| c.trim().to_string()).filter(|s| !s.is_empty() && s != "auto")
}

pub fn set_preferred_device(serial: &str) {
    if let Some(path) = preferred_file_path() {
        if let Some(parent) = path.parent() { let _ = std::fs::create_dir_all(parent); }
        let _ = std::fs::write(path, serial.trim());
    }
}

pub fn add_paired_device(ident: &str) -> bool {
    let mut paired: Vec<String> = paired_file_path().and_then(|p| std::fs::read_to_string(p).ok())
        .and_then(|c| serde_json::from_str(&c).ok()).unwrap_or_default();
    if !paired.contains(&ident.to_string()) {
        paired.push(ident.to_string());
        if let Some(path) = paired_file_path() {
            if let Some(parent) = path.parent() { let _ = std::fs::create_dir_all(parent); }
            let _ = std::fs::write(path, serde_json::to_string_pretty(&paired).unwrap_or_default());
        }
        return true;
    }
    false
}

pub fn remove_paired_device(ident: &str) -> bool {
    let mut paired: Vec<String> = paired_file_path().and_then(|p| std::fs::read_to_string(p).ok())
        .and_then(|c| serde_json::from_str(&c).ok()).unwrap_or_default();
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

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn empty_paired_allows_any_device() {
        let authorized = get_authorized_devices();
        let legacy = paired_file_path().and_then(|p| std::fs::read_to_string(p).ok())
            .and_then(|c| serde_json::from_str::<Vec<String>>(&c).ok()).unwrap_or_default();
        if authorized.is_empty() && legacy.is_empty() {
            assert!(is_device_paired("dummy_serial", Some("dummy_model")));
        }
    }
}
