//! Re-export pure domain events from singularity-protocol.
pub use singularity_protocol::DeviceEvent;

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn reexported_event_functions_normally() {
        let event = DeviceEvent::ClipboardChanged {
            text: "test".into(),
            sequence: 1,
        };
        let json = serde_json::to_string(&event).unwrap();
        assert!(json.contains("ClipboardChanged"));
    }
}
