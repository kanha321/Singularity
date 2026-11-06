use singularity_protocol::{CameraLens, CommandResponse, FeatureCommand};
use crate::error::Result;
use super::state::CameraState;

pub struct CameraCommandHandler<'a> {
    state: &'a CameraState,
}

impl<'a> CameraCommandHandler<'a> {
    pub fn new(state: &'a CameraState) -> Self {
        Self { state }
    }

    pub fn handle(&self, command: &FeatureCommand) -> Result<CommandResponse> {
        match command.action.as_str() {
            "start" => {
                self.state.set_active(true);
                Ok(CommandResponse::Ok(serde_json::json!({ "message": "camera started" })))
            }
            "stop" => {
                self.state.set_active(false);
                self.state.set_torch(false);
                Ok(CommandResponse::Ok(serde_json::json!({ "message": "camera stopped" })))
            }
            "torch" => {
                let enabled = command.payload.get("enabled")
                    .and_then(|v| v.as_bool())
                    .unwrap_or(false);
                self.state.set_torch(enabled);
                Ok(CommandResponse::Ok(serde_json::json!({ "torch": enabled })))
            }
            "zoom" => {
                let level = command.payload.get("zoom")
                    .and_then(|v| v.as_f64())
                    .map(|f| f as f32)
                    .unwrap_or(1.0);
                let applied = self.state.set_zoom(level);
                Ok(CommandResponse::Ok(serde_json::json!({ "zoom": applied })))
            }
            "lens" => {
                let lens_str = command.payload.get("lens")
                    .and_then(|v| v.as_str())
                    .unwrap_or("main");
                let lens = match lens_str {
                    "ultrawide" => CameraLens::Ultrawide,
                    "front" => CameraLens::Front,
                    _ => CameraLens::Main,
                };
                self.state.set_lens(lens);
                Ok(CommandResponse::Ok(serde_json::json!({ "lens": lens_str })))
            }
            _ => Ok(CommandResponse::Unsupported),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn handle_start_stop() {
        let state = CameraState::new();
        let handler = CameraCommandHandler::new(&state);

        let cmd = FeatureCommand {
            target_feature: "camera".into(),
            action: "start".into(),
            payload: serde_json::Value::Null,
        };
        let resp = handler.handle(&cmd).unwrap();
        assert!(matches!(resp, CommandResponse::Ok(_)));
        assert!(state.is_active());

        let stop = FeatureCommand {
            target_feature: "camera".into(),
            action: "stop".into(),
            payload: serde_json::Value::Null,
        };
        let resp = handler.handle(&stop).unwrap();
        assert!(matches!(resp, CommandResponse::Ok(_)));
        assert!(!state.is_active());
    }

    #[test]
    fn handle_torch_and_zoom() {
        let state = CameraState::new();
        let handler = CameraCommandHandler::new(&state);

        let cmd = FeatureCommand {
            target_feature: "camera".into(),
            action: "torch".into(),
            payload: serde_json::json!({ "enabled": true }),
        };
        handler.handle(&cmd).unwrap();
        assert!(state.to_status().torch);

        let zoom_cmd = FeatureCommand {
            target_feature: "camera".into(),
            action: "zoom".into(),
            payload: serde_json::json!({ "zoom": 2.5 }),
        };
        handler.handle(&zoom_cmd).unwrap();
        assert_eq!(state.to_status().zoom, 2.5);
    }
}
