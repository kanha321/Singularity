use clap::Subcommand;
use singularity_client::SingularityClient;
use singularity_protocol::{CameraConfig, CameraLens, CameraResolution};

#[derive(Subcommand, Debug, Clone)]
pub enum CameraAction {
    /// Start webcam streaming from phone
    Start {
        /// Camera lens (main, ultrawide, front)
        #[arg(short, long, default_value = "main")]
        lens: String,

        /// Target frame rate (15, 24, 30)
        #[arg(short, long, default_value_t = 30)]
        fps: u32,

        /// Disable PipeWire virtual microphone
        #[arg(long)]
        no_mic: bool,
    },
    /// Stop webcam streaming
    Stop,
    /// Inspect webcam stream and device status
    Status,
    /// Switch active camera lens (main, ultrawide, front)
    Lens {
        /// Target lens name
        name: String,
    },
    /// Toggle hardware torch / fill-light
    Torch {
        /// Enable or disable torch
        enabled: bool,
    },
    /// Set digital zoom level (0.65 to 10.0)
    Zoom {
        /// Zoom multiplier
        level: f32,
    },
}

pub async fn handle_camera_cmd(
    client: &SingularityClient,
    action: CameraAction,
) -> Result<(), Box<dyn std::error::Error>> {
    match action {
        CameraAction::Start { lens, fps, no_mic } => {
            let selected_lens = match lens.to_lowercase().as_str() {
                "ultrawide" | "wide" | "0.65" => CameraLens::Ultrawide,
                "front" | "selfie" => CameraLens::Front,
                _ => CameraLens::Main,
            };
            let config = CameraConfig {
                lens: selected_lens,
                resolution: CameraResolution::P1080,
                fps,
                enable_mic: !no_mic,
                device_path: "/dev/video10".into(),
                auto_demand: true,
            };
            println!("📷 Starting Singularity Continuity Camera on /dev/video10...");
            let status = client.start_camera(Some(config)).await?;
            println!("✅ Camera active: {:?} ({}x{}) @ {}fps | Mic: {}",
                status.lens,
                status.resolution.dimensions().0, status.resolution.dimensions().1,
                status.fps,
                if status.has_mic { "enabled" } else { "disabled" }
            );
        }
        CameraAction::Stop => {
            client.stop_camera().await?;
            println!("⏹️ Continuity Camera stopped");
        }
        CameraAction::Status => {
            let status = client.get_camera_status().await?;
            println!("📷 Continuity Camera Status:");
            println!("   State:       {}", if status.active { "🟢 Active (Streaming)" } else { "⚪ Idle" });
            println!("   Lens:        {:?}", status.lens);
            println!("   Resolution:  {}", status.resolution.as_str());
            println!("   FPS:         {}", status.fps);
            println!("   Torch:       {}", if status.torch { "ON" } else { "OFF" });
            println!("   Zoom:        {:.2}x", status.zoom);
            println!("   Microphone:  {}", if status.has_mic { "Piped to PipeWire" } else { "Inactive" });
            println!("   App Readers: {}", status.readers_count);
        }
        CameraAction::Lens { name } => {
            let selected_lens = match name.to_lowercase().as_str() {
                "ultrawide" | "wide" | "0.65" => CameraLens::Ultrawide,
                "front" | "selfie" => CameraLens::Front,
                _ => CameraLens::Main,
            };
            let status = client.switch_camera_lens(selected_lens).await?;
            println!("🔄 Lens switched to {:?}", status.lens);
        }
        CameraAction::Torch { enabled } => {
            client.set_camera_torch(enabled).await?;
            println!("🔦 Torch set to {}", if enabled { "ON" } else { "OFF" });
        }
        CameraAction::Zoom { level } => {
            client.set_camera_zoom(level).await?;
            println!("🔍 Zoom set to {:.2}x", level);
        }
    }
    Ok(())
}

