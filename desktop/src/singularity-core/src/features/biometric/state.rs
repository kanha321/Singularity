#[derive(Debug, Clone, Default)]
pub struct BiometricState {
    pub is_available: bool,
    pub total_auth_count: u64,
    pub successful_auth_count: u64,
    pub last_auth_success: Option<bool>,
}

impl BiometricState {
    pub fn new() -> Self {
        Self::default()
    }

    pub fn set_available(&mut self, available: bool) {
        self.is_available = available;
    }

    pub fn record_result(&mut self, success: bool) {
        self.total_auth_count += 1;
        if success {
            self.successful_auth_count += 1;
        }
        self.last_auth_success = Some(success);
    }

    pub fn reset(&mut self) {
        self.is_available = false;
        self.last_auth_success = None;
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn biometric_state_transitions_and_counters() {
        let mut state = BiometricState::new();
        assert!(!state.is_available);
        assert_eq!(state.total_auth_count, 0);

        state.set_available(true);
        assert!(state.is_available);

        state.record_result(true);
        assert_eq!(state.total_auth_count, 1);
        assert_eq!(state.successful_auth_count, 1);
        assert_eq!(state.last_auth_success, Some(true));

        state.record_result(false);
        assert_eq!(state.total_auth_count, 2);
        assert_eq!(state.successful_auth_count, 1);
        assert_eq!(state.last_auth_success, Some(false));

        state.reset();
        assert!(!state.is_available);
        assert!(state.last_auth_success.is_none());
    }
}
