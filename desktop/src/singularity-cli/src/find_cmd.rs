use singularity_client::SingularityClient;

pub async fn handle_find_cmd(
    client: &SingularityClient,
    stop: bool,
) -> Result<(), Box<dyn std::error::Error>> {
    if stop {
        println!("🔕 Silencing phone siren...");
        client.find_device(false).await?;
        println!("✅ Phone siren stopped.");
    } else {
        println!("🔔 Ringing phone at 100% volume (overriding DND)...");
        println!("   Press Ctrl+C or run 'singularity find --stop' to cancel.");
        client.find_device(true).await?;
        println!("📢 Siren active! Look for your device.");
    }
    Ok(())
}

