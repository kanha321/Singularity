use std::collections::HashMap;
use std::sync::Arc;
use tokio::io::{AsyncReadExt, AsyncWriteExt};
use tokio::sync::RwLock;

pub async fn handle_browser_options<W: AsyncWriteExt + Unpin>(
    write_half: &mut W,
) -> std::io::Result<()> {
    let resp = "HTTP/1.1 204 No Content\r\n\
Access-Control-Allow-Origin: *\r\n\
Access-Control-Allow-Methods: POST, OPTIONS\r\n\
Access-Control-Allow-Headers: Content-Type\r\n\
Connection: close\r\n\r\n";
    write_half.write_all(resp.as_bytes()).await?;
    write_half.flush().await
}

pub async fn handle_browser_post<R: AsyncReadExt + Unpin, W: AsyncWriteExt + Unpin>(
    headers: &HashMap<String, String>,
    reader: &mut R,
    write_half: &mut W,
    active_device: &Arc<RwLock<String>>,
) -> std::io::Result<()> {
    let content_len = headers
        .get("content-length")
        .and_then(|v| v.parse::<usize>().ok())
        .unwrap_or(0);

    let mut body = vec![0u8; content_len];
    if content_len > 0 {
        reader.read_exact(&mut body).await?;
    }

    #[derive(serde::Deserialize)]
    struct OpenUrlReq {
        url: Option<String>,
    }

    let parsed: Result<OpenUrlReq, _> = serde_json::from_slice(&body);
    let url = match parsed {
        Ok(req) if req.url.is_some() => req.url.unwrap(),
        _ => {
            let resp = "HTTP/1.1 400 Bad Request\r\n\
Access-Control-Allow-Origin: *\r\n\
Content-Type: application/json\r\n\
Connection: close\r\n\r\n{\"error\":\"Missing or invalid url field\"}";
            write_half.write_all(resp.as_bytes()).await?;
            return write_half.flush().await;
        }
    };

    match crate::transport::resolve_or_wait_device(active_device, 2).await {
        Some(addr) => {
            let runner = Arc::new(crate::transport::AdbShellRunner::new(&addr));
            let shell = singularity_core::bridge::ShellBridge::with_runner(runner);
            match singularity_core::features::browser::launch_url_on_device(&shell, &url).await {
                Ok(_) => {
                    let resp = "HTTP/1.1 200 OK\r\n\
Access-Control-Allow-Origin: *\r\n\
Content-Type: application/json\r\n\
Connection: close\r\n\r\n{\"status\":\"ok\"}";
                    write_half.write_all(resp.as_bytes()).await?;
                }
                Err(e) => {
                    let err_body = format!("{{\"error\":\"{}\"}}", e);
                    let resp = format!(
                        "HTTP/1.1 500 Internal Server Error\r\nAccess-Control-Allow-Origin: *\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}",
                        err_body.len(),
                        err_body
                    );
                    write_half.write_all(resp.as_bytes()).await?;
                }
            }
        }
        None => {
            let resp = "HTTP/1.1 503 Service Unavailable\r\n\
Access-Control-Allow-Origin: *\r\n\
Content-Type: application/json\r\n\
Connection: close\r\n\r\n{\"error\":\"No Android device detected\"}";
            write_half.write_all(resp.as_bytes()).await?;
        }
    }

    write_half.flush().await
}

pub async fn handle_desktop_open_post<R: AsyncReadExt + Unpin, W: AsyncWriteExt + Unpin>(
    headers: &HashMap<String, String>,
    reader: &mut R,
    write_half: &mut W,
) -> std::io::Result<()> {
    let content_len = headers
        .get("content-length")
        .and_then(|v| v.parse::<usize>().ok())
        .unwrap_or(0);

    let mut body = vec![0u8; content_len];
    if content_len > 0 {
        reader.read_exact(&mut body).await?;
    }

    #[derive(serde::Deserialize)]
    struct OpenUrlReq {
        url: Option<String>,
    }

    let parsed: Result<OpenUrlReq, _> = serde_json::from_slice(&body);
    let url = match parsed {
        Ok(req) if req.url.is_some() => req.url.unwrap(),
        _ => {
            let resp = "HTTP/1.1 400 Bad Request\r\n\
Access-Control-Allow-Origin: *\r\n\
Content-Type: application/json\r\n\
Connection: close\r\n\r\n{\"error\":\"Missing or invalid url field\"}";
            write_half.write_all(resp.as_bytes()).await?;
            return write_half.flush().await;
        }
    };

    tracing::info!(target: "file_server", %url, "Opening URL on desktop via HTTP request");
    tokio::spawn(async move {
        let _ = tokio::process::Command::new("xdg-open").arg(&url).status().await;
    });

    let resp = "HTTP/1.1 200 OK\r\n\
Access-Control-Allow-Origin: *\r\n\
Content-Type: application/json\r\n\
Connection: close\r\n\r\n{\"status\":\"ok\"}";
    write_half.write_all(resp.as_bytes()).await?;
    write_half.flush().await
}

