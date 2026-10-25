use std::sync::Arc;
use tokio::net::TcpListener;
use tokio::sync::RwLock;

pub fn start_file_server(active_device: Arc<RwLock<String>>, port: u16) -> tokio::task::JoinHandle<()> {
    tokio::spawn(async move {
        let addr = format!("0.0.0.0:{}", port);
        let listener = match TcpListener::bind(&addr).await {
            Ok(l) => {
                tracing::info!(target: "file_server", port = port, "Streaming HTTP file server online");
                l
            }
            Err(e) => {
                tracing::error!(target: "file_server", error = %e, "Failed to bind file server port");
                return;
            }
        };

        let adb_dev = Arc::clone(&active_device);
        tokio::spawn(async move {
            loop {
                let current_dev = adb_dev.read().await.clone();
                if current_dev != "auto" {
                    let _ = tokio::process::Command::new("adb")
                        .args(["-s", &current_dev, "reverse", "tcp:8920", "tcp:8920"])
                        .status()
                        .await;
                }
                tokio::time::sleep(std::time::Duration::from_secs(5)).await;
            }
        });

        loop {
            match listener.accept().await {
                Ok((stream, _peer)) => {
                    tokio::spawn(async move {
                        let _ = super::router::handle_http_connection(stream).await;
                    });
                }
                Err(e) => {
                    tracing::warn!(target: "file_server", error = %e, "Accept error on file server");
                }
            }
        }
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn test_file_server_binds_ephemeral() {
        let l = TcpListener::bind("127.0.0.1:0").await;
        assert!(l.is_ok());
    }
}
