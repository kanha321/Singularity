use std::path::PathBuf;
use std::time::Instant;

#[derive(Debug)]
pub struct StorageState {
    pub is_mounted: bool,
    pub mount_point: Option<PathBuf>,
    pub remote_path: String,
    pub mounted_at: Option<Instant>,
    pub has_root: bool,
}

impl StorageState {
    pub fn new() -> Self {
        Self {
            is_mounted: false,
            mount_point: None,
            remote_path: "/".into(),
            mounted_at: None,
            has_root: false,
        }
    }

    pub fn set_has_root(&mut self, has_root: bool) {
        self.has_root = has_root;
    }

    pub fn set_mounted(&mut self, mount_point: PathBuf, remote_path: String) {
        self.is_mounted = true;
        self.mount_point = Some(mount_point);
        self.remote_path = remote_path;
        self.mounted_at = Some(Instant::now());
    }

    pub fn set_unmounted(&mut self) {
        self.is_mounted = false;
        self.mount_point = None;
        self.mounted_at = None;
    }
}

impl Default for StorageState {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn state_transitions_correctly() {
        let mut state = StorageState::new();
        assert!(!state.is_mounted);

        state.set_mounted(PathBuf::from("/home/user/Phone"), "/sdcard".into());
        assert!(state.is_mounted);
        assert_eq!(state.remote_path, "/sdcard");
        assert!(state.mounted_at.is_some());

        state.set_unmounted();
        assert!(!state.is_mounted);
        assert!(state.mount_point.is_none());

        assert!(!state.has_root);
        state.set_has_root(true);
        assert!(state.has_root);
    }
}
