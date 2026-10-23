mod assets;
mod biometric;
mod clipboard;
mod config;
mod deployer;
mod error;
mod lock_watcher;
mod server;
mod singleton;
mod storage;
mod supervisor;
mod transport;
mod wayland;

use std::sync::Arc;
use clap::Parser;
use config::DaemonConfig;
use server::IpcServer;
use singularity_core::{BiometricFeature, CallingFeature, ClipboardFeature, FeatureRegistry, StorageFeature};
use singleton::SingletonLock;
use tracing_subscriber::{layer::SubscriberExt, util::SubscriberInitExt};

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    tracing_subscriber::registry()
        .with(tracing_subscriber::EnvFilter::try_from_default_env().unwrap_or_else(|_| "singularity=debug,info".into()))
        .with(tracing_subscriber::fmt::layer())
        .init();

    let config = DaemonConfig::parse();
    let socket_path = config.resolved_socket_path();
    let _singleton = SingletonLock::acquire(&socket_path)?;

    tracing::info!("Starting Singularity Daemon v{}", env!("CARGO_PKG_VERSION"));
    if config.simulate_non_root {
        tracing::warn!("⚠️  SIMULATE NON-ROOT ACTIVE: DeviceBridge will treat target as unrooted (root: None)");
    }

    let (clip_tx, _) = tokio::sync::broadcast::channel::<String>(32);

    let mut reg = FeatureRegistry::new();
    reg.register(Box::new(ClipboardFeature::default()));
    reg.register(Box::new(CallingFeature::default()));
    reg.register(Box::new(StorageFeature::default()));
    reg.register(Box::new(BiometricFeature::default()));
    let registry = Arc::new(tokio::sync::RwLock::new(reg));

    let storage_tunnel = Arc::new(tokio::sync::Mutex::new(None));
    let active_mount = Arc::new(tokio::sync::Mutex::new(None));
    let has_root = Arc::new(std::sync::atomic::AtomicBool::new(false));
    let active_device = Arc::new(tokio::sync::RwLock::new(config.adb_address.clone()));

    let server = IpcServer::bind(
        &socket_path,
        clip_tx.clone(),
        Arc::clone(&registry),
        Arc::clone(&active_device),
        Arc::clone(&storage_tunnel),
        Arc::clone(&active_mount),
        Arc::clone(&has_root),
    )?;

    let _watcher_handle = match wayland::start_wayland_watcher(clip_tx.clone()) {
        Ok(h) => { tracing::info!(target: "daemon", "Native Wayland clipboard watcher online"); Some(h) }
        Err(e) => { tracing::warn!(target: "daemon", error = %e, "Wayland clipboard watcher unavailable"); None }
    };

    let _lock_handle = lock_watcher::start_lock_watcher(Arc::clone(&active_device));

    let supervisor = supervisor::start_supervisor(
        config.adb_address,
        config.reconnect_interval_secs,
        config.simulate_non_root,
        active_device,
        registry,
        clip_tx,
        Arc::clone(&active_mount),
        Arc::clone(&storage_tunnel),
        Arc::clone(&has_root),
    );

    tokio::select! {
        _ = server.accept_loop() => {},
        _ = supervisor => {},
        _ = tokio::signal::ctrl_c() => {
            tracing::info!("Received SIGINT, initiating graceful shutdown");
        }
    }

    if let Some(mount) = active_mount.lock().await.take() {
        let _ = storage::unmount_phone(&mount);
    }

    if socket_path.exists() {
        let _ = std::fs::remove_file(&socket_path);
    }

    tracing::info!("Singularity Daemon stopped");
    Ok(())
}
