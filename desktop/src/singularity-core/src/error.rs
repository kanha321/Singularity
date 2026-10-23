use thiserror::Error;

#[derive(Error, Debug)]
pub enum CoreError {
    #[error("Bridge error in {bridge}: {message}")]
    BridgeFailure {
        bridge: &'static str,
        message: String,
    },

    #[error("Required bridge '{0}' is not available on this device")]
    BridgeUnavailable(&'static str),

    #[error("Feature error in '{feature}': {message}")]
    FeatureError {
        feature: &'static str,
        message: String,
    },

    #[error("Event dispatch broadcast error: {0}")]
    BroadcastError(String),

    #[error("Serialization error: {0}")]
    Serialization(#[from] serde_json::Error),
}

pub type Result<T> = std::result::Result<T, CoreError>;

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn bridge_failure_displays_formatted_message() {
        let err = CoreError::BridgeFailure {
            bridge: "RootBridge",
            message: "sysfs node not found".into(),
        };
        assert_eq!(err.to_string(), "Bridge error in RootBridge: sysfs node not found");
    }

    #[test]
    fn bridge_unavailable_displays_bridge_name() {
        let err = CoreError::BridgeUnavailable("RootBridge");
        assert_eq!(err.to_string(), "Required bridge 'RootBridge' is not available on this device");
    }
}
