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
    Clip {
        /// Text to synchronize
        text: String,
    },

    /// Mount phone storage via Root SFTP and FUSE
    Mount {
        /// Remote path on device to mount (e.g. / or /sdcard, default: /data/media/0)
        path: Option<String>,
    },

    /// Unmount phone storage
    Unmount,

    /// Trigger biometric fingerprint authentication on connected phone
    Auth {
        /// Reason displayed on phone prompt
        #[arg(short, long, default_value = "CLI verification")]
        reason: String,

        /// Timeout in seconds
        #[arg(short, long, default_value_t = 15)]
        timeout: u32,
    },

    /// Control telephony and calls
    Call {
        #[command(subcommand)]
        action: CallAction,
    },
}

#[derive(Subcommand)]
enum CallAction {
    /// Answer an incoming telephone call
    Answer,
    /// Terminate an active or ringing phone call
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
                        FeatureState::Degraded { reason } => ("🟡", format!("({})", reason)),
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
            CallAction::Answer => {
                client.accept_call().await?;
                println!("Call accepted");
            }
            CallAction::End => {
                client.end_call().await?;
                println!("Call ended");
            }
        },
    }

    Ok(())
}
