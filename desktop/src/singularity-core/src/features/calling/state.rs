#[derive(Debug, Clone, Default)]
pub struct CallingState {
    pub is_in_call: bool,
    pub active_caller: Option<String>,
}

impl CallingState {
    pub fn new() -> Self {
        Self::default()
    }

    pub fn set_ringing(&mut self, caller: String) {
        self.active_caller = Some(caller);
    }

    pub fn set_active(&mut self) {
        self.is_in_call = true;
    }

    pub fn set_idle(&mut self) {
        self.is_in_call = false;
        self.active_caller = None;
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn calling_state_transitions() {
        let mut state = CallingState::new();
        assert!(!state.is_in_call);

        state.set_ringing("Alice".into());
        assert_eq!(state.active_caller.as_deref(), Some("Alice"));

        state.set_active();
        assert!(state.is_in_call);

        state.set_idle();
        assert!(!state.is_in_call);
        assert!(state.active_caller.is_none());
    }
}
