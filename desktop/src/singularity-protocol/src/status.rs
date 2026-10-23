use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub enum RequirementLevel {
    Required,
    Optional,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct FeatureRequirements {
    pub root: RequirementLevel,
    pub shell: RequirementLevel,
    pub app: RequirementLevel,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub enum FeatureState {
    Active,
    Degraded { reason: String },
    Inactive,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct FeatureStatus {
    pub id: String,
    pub name: String,
    pub state: FeatureState,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct FeatureCommand {
    pub target_feature: String,
    pub action: String,
    pub payload: serde_json::Value,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub enum CommandResponse {
    Ok(serde_json::Value),
    Err(String),
    Unsupported,
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn feature_status_roundtrip() {
        let status = FeatureStatus {
            id: "clipboard".into(),
            name: "Universal Clipboard".into(),
            state: FeatureState::Active,
        };
        let json = serde_json::to_string(&status).unwrap();
        let parsed: FeatureStatus = serde_json::from_str(&json).unwrap();
        assert_eq!(status, parsed);
    }

    #[test]
    fn degraded_state_serializes_reason() {
        let state = FeatureState::Degraded {
            reason: "Companion app not installed".into(),
        };
        let json = serde_json::to_string(&state).unwrap();
        assert!(json.contains("Companion app not installed"));
    }
}
