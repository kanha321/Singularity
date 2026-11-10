use clap::Subcommand;
use singularity_client::SingularityClient;

#[derive(Subcommand)]
pub enum NotificationAction {
    /// List active phone notifications
    List,
    /// Send an inline quick reply to a phone notification
    Reply { id: String, message: String },
    /// Invoke an action button on a phone notification
    Action { id: String, action_id: String },
}

pub async fn handle_notification_cmd(
    client: &SingularityClient,
    action: NotificationAction,
) -> Result<(), Box<dyn std::error::Error>> {
    match action {
        NotificationAction::List => {
            let notifs = client.list_notifications().await?;
            if notifs.is_empty() {
                println!("No active phone notifications.");
            } else {
                println!("{:<18} {:<24} {:<6} ACTIONS", "APP", "TITLE", "REPLY");
                println!("{:-<18} {:-<24} {:-<6} {:-<20}", "", "", "", "");
                for n in notifs {
                    let rep = if n.can_reply { "yes" } else { "no" };
                    let acts = n.actions.iter().map(|a| a.label.as_str()).collect::<Vec<_>>().join(", ");
                    println!("{:<18} {:<24} {:<6} {}", n.app_name, n.title, rep, acts);
                }
            }
        }
        NotificationAction::Reply { id, message } => {
            client.reply_notification(&id, &message).await?;
            println!("💬 Reply dispatched to notification {id}");
        }
        NotificationAction::Action { id, action_id } => {
            client.invoke_notification_action(&id, &action_id).await?;
            println!("⚡ Action '{action_id}' dispatched to notification {id}");
        }
    }
    Ok(())
}
