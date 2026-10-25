pub mod handler;
pub mod auth_handler;

use std::path::{Path, PathBuf};
use std::sync::Arc;
use tokio::net::UnixListener;
use tokio::sync::broadcast::Sender;
use tokio::sync::{Mutex, RwLock};
use singularity_core::FeatureRegistry;
use crate::auth_cache::SharedAuthCache;
use crate::error::{DaemonError, Result};
use crate::transport::AdbTunnel;

pub struct IpcServer {
    listener: UnixListener,
    clip_tx: Sender<String>,
    registry: Arc<RwLock<FeatureRegistry>>,
    active_device: Arc<RwLock<String>>,
    storage_tunnel: Arc<Mutex<Option<AdbTunnel>>>,
    active_mount: Arc<Mutex<Option<PathBuf>>>,
    has_root: Arc<std::sync::atomic::AtomicBool>,
    auth_cache: SharedAuthCache,
}

impl IpcServer {
    #[allow(clippy::too_many_arguments)]
    pub fn bind<P: AsRef<Path>>(
        path: P,
        clip_tx: Sender<String>,
        registry: Arc<RwLock<FeatureRegistry>>,
        active_device: Arc<RwLock<String>>,
        storage_tunnel: Arc<Mutex<Option<AdbTunnel>>>,
        active_mount: Arc<Mutex<Option<PathBuf>>>,
        has_root: Arc<std::sync::atomic::AtomicBool>,
        auth_cache: SharedAuthCache,
    ) -> Result<Self> {
        let path = path.as_ref();
        if path.exists() {
            let _ = std::fs::remove_file(path);
        }
        if let Some(parent) = path.parent() {
            let _ = std::fs::create_dir_all(parent);
        }

        let listener = UnixListener::bind(path)
            .map_err(|e| DaemonError::SocketBindFailed(path.display().to_string(), e))?;

        tracing::info!(target: "ipc_server", path = %path.display(), "UNIX domain socket bound successfully");
        Ok(Self {
            listener,
            clip_tx,
            registry,
            active_device,
            storage_tunnel,
            active_mount,
            has_root,
            auth_cache,
        })
    }

    pub async fn accept_loop(&self) {
        loop {
            match self.listener.accept().await {
                Ok((stream, _)) => {
                    let clip_tx = self.clip_tx.clone();
                    let registry = Arc::clone(&self.registry);
                    let active_device = Arc::clone(&self.active_device);
                    let storage_tunnel = Arc::clone(&self.storage_tunnel);
                    let active_mount = Arc::clone(&self.active_mount);
                    let has_root = Arc::clone(&self.has_root);
                    let auth_cache = Arc::clone(&self.auth_cache);

                    tokio::spawn(async move {
                        if let Err(e) = handler::handle_client(
                            stream,
                            clip_tx,
                            registry,
                            active_device,
                            storage_tunnel,
                            active_mount,
                            has_root,
                            auth_cache,
                        )
                        .await
                        {
                            tracing::warn!(target: "ipc_server", error = %e, "Client connection ended with error");
                        }
                    });
                }
                Err(e) => {
                    tracing::error!(target: "ipc_server", error = %e, "Failed to accept client connection");
                }
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn server_binds_and_creates_temp_socket() {
        let (tx, _) = tokio::sync::broadcast::channel(16);
        let registry = Arc::new(RwLock::new(FeatureRegistry::new()));
        let storage_tunnel = Arc::new(Mutex::new(None));
        let active_mount = Arc::new(Mutex::new(None));
        let has_root = Arc::new(std::sync::atomic::AtomicBool::new(false));
        let auth_cache = crate::auth_cache::new_auth_cache();
        let temp_dir = tempfile::tempdir().unwrap();
        let socket_path = temp_dir.path().join("test_singularity.sock");
        let server = IpcServer::bind(
            &socket_path,
            tx,
            registry,
            Arc::new(RwLock::new("127.0.0.1:5555".into())),
            storage_tunnel,
            active_mount,
            has_root,
            auth_cache,
        );
        assert!(server.is_ok());
        assert!(socket_path.exists());
    }
}
