use singularity_client::SingularityClient;

pub async fn handle_devices_cmd(client: &SingularityClient) -> Result<(), Box<dyn std::error::Error>> {
    let devices = client.list_devices().await?;
    if devices.is_empty() {
        println!("No devices detected via ADB or network.");
        return Ok(());
    }
    println!("⚡ Singularity Detected Devices:\n");
    println!("{:<22} {:<8} {:<16} STATUS", "SERIAL", "TYPE", "MODEL");
    println!("{:-<22} {:-<8} {:-<16} {:-<20}", "", "", "", "");
    for d in devices {
        let status_str = match (d.is_active, d.is_paired) {
            (true, true) => "🟢 ACTIVE (PAIRED)",
            (true, false) => "🟢 ACTIVE (UNPAIRED)",
            (false, true) => "⚪ CONNECTED (PAIRED)",
            (false, false) => "⚪ CONNECTED (UNPAIRED)",
        };
        let model = d.model.unwrap_or_else(|| "Unknown".into());
        println!("{:<22} {:<8} {:<16} {}", d.serial, d.connection_type.to_uppercase(), model, status_str);
    }
    Ok(())
}

pub async fn handle_use_cmd(client: &SingularityClient, serial: &str) -> Result<(), Box<dyn std::error::Error>> {
    client.select_device(serial).await?;
    println!("✅ Switched active target device to: {}", serial);
    Ok(())
}

pub async fn handle_pair_cmd(client: &SingularityClient, serial: &str) -> Result<(), Box<dyn std::error::Error>> {
    client.pair_device(serial).await?;
    println!("✅ Added to paired whitelist: {}", serial);
    Ok(())
}

pub async fn handle_unpair_cmd(client: &SingularityClient, serial: &str) -> Result<(), Box<dyn std::error::Error>> {
    client.unpair_device(serial).await?;
    println!("✅ Removed from paired whitelist: {}", serial);
    Ok(())
}

