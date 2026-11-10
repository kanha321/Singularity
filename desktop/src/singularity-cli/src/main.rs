mod camera_cmd;
mod device_cmd;
mod notification_cmd;

use clap::{Parser, Subcommand};
use singularity_client::{FeatureState, SingularityClient};

#[derive(Parser)]
#[command(name = "singularity", about = "Singularity Convergence CLI")]
struct Cli {
    #[command(subcommand)]
    command: Commands,
}

#[derive(Subcommand)]
enum Commands {
    /// Inspect daemon connection and feature statuses
    Status,
    /// Push text to device clipboard
    Clip { text: String },
    /// Mount phone storage via Root SFTP and FUSE
    Mount { path: Option<String> },
    /// Unmount phone storage
    Unmount,
    /// Trigger biometric fingerprint authentication on connected phone
    Auth {
        #[arg(short, long, default_value = "CLI verification")]
        reason: String,
        #[arg(short, long, default_value_t = 15)]
        timeout: u32,
    },
    /// Control telephony and calls
    Call {
        #[command(subcommand)]
        action: CallAction,
    },
    /// Open a URL or hyperlink on connected Android device
    OpenUrl { url: String },
    /// Remount system partitions as read-write or read-only (requires root)
    Remount {
        #[arg(long, conflicts_with = "ro")]
        rw: bool,
        #[arg(long, conflicts_with = "rw")]
        ro: bool,
    },
    /// Control continuity camera and phone webcam streaming
    Camera {
        #[command(subcommand)]
        action: camera_cmd::CameraAction,
    },
    /// List detected, connected and paired devices
    Devices,
    /// Switch active target device by serial or IP
    Use { serial: String },
    /// Add a device to the trusted paired whitelist
    Pair { serial: String },
    /// Remove a device from the trusted paired whitelist
    Unpair { serial: String },
    /// Quick reply to phone notification
    Reply { id: String, message: String },
    /// Notification actions & replies
    Notification {
        #[command(subcommand)]
        action: notification_cmd::NotificationAction,
    },
    /// List active phone notifications
    Notifications,
}

#[derive(Subcommand)]
enum CallAction {
    Answer,
    End,
}

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    let cli = Cli::parse();
    let client = SingularityClient::new();

    match cli.command {
        Commands::Status => {
            let statuses = client.get_status().await?;
            println!("⚡ Singularity Daemon: Online");
            if statuses.is_empty() {
                println!("  No active features registered with daemon.");
            } else {
                println!("\n{:<4} {:<24} {:<12} DETAILS", "STATE", "FEATURE", "ID");
                println!("{:-<5} {:-<24} {:-<12} {:-<20}", "", "", "", "");
                for feat in statuses {
                    let (icon, details) = match feat.state {
                        FeatureState::Active => ("🟢", String::new()),
                        FeatureState::Degraded { reason } => ("🟡", format!("({reason})")),
                        FeatureState::Inactive => ("⚪", String::new()),
                    };
                    println!("{:<4} {:<24} {:<12} {}", icon, feat.name, feat.id, details);
                }
            }
        }
        Commands::Clip { text } => {
            client.set_clipboard(&text).await?;
            println!("Clipboard synchronized ({} bytes)", text.len());
        }
        Commands::Mount { path } => {
            let mount_point = client.mount_storage(path).await?;
            println!("Mounted phone storage at {}", mount_point);
        }
        Commands::Unmount => {
            client.unmount_storage().await?;
            println!("Unmounted phone storage");
        }
        Commands::Auth { reason, timeout } => {
            println!("📲 Prompting for biometric authentication on phone...");
            match client.authenticate_biometric(&reason, timeout).await {
                Ok(true) => println!("✅ Biometric authentication successful!"),
                Ok(false) => println!("❌ Biometric authentication failed or cancelled."),
                Err(e) => eprintln!("⚠️ Biometric error: {}", e),
            }
        }
        Commands::Call { action } => match action {
            CallAction::Answer => { client.accept_call().await?; println!("Call accepted"); }
            CallAction::End => { client.end_call().await?; println!("Call ended"); }
        },
        Commands::OpenUrl { url } => {
            client.open_url(&url).await?;
            println!("✅ Dispatched URL to Android: {}", url);
        }
        Commands::Remount { rw: _, ro } => {
            let is_rw = !ro;
            let res = client.remount_system(is_rw).await?;
            if is_rw {
                println!("🔓 System partitions remounted as {res}. Run 'singularity remount --ro' when done.");
            } else {
                println!("🔒 System partitions locked as {res}.");
            }
        }
        Commands::Camera { action } => camera_cmd::handle_camera_cmd(&client, action).await?,
        Commands::Devices => device_cmd::handle_devices_cmd(&client).await?,
        Commands::Use { serial } => device_cmd::handle_use_cmd(&client, &serial).await?,
        Commands::Pair { serial } => device_cmd::handle_pair_cmd(&client, &serial).await?,
        Commands::Unpair { serial } => device_cmd::handle_unpair_cmd(&client, &serial).await?,
        Commands::Reply { id, message } => notification_cmd::handle_notification_cmd(&client, notification_cmd::NotificationAction::Reply { id, message }).await?,
        Commands::Notification { action } => notification_cmd::handle_notification_cmd(&client, action).await?,
        Commands::Notifications => notification_cmd::handle_notification_cmd(&client, notification_cmd::NotificationAction::List).await?,
    }

    Ok(())
}
