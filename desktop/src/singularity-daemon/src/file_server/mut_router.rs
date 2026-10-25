use std::collections::HashMap;
use serde::Deserialize;
use tokio::io::{AsyncReadExt, AsyncWriteExt};
use super::archive_dispatch::{dispatch_batch, dispatch_delete, dispatch_mkdir, dispatch_rename};

#[derive(Deserialize)]
struct MkdirReq {
    path: String,
}

#[derive(Deserialize)]
struct RenameReq {
    old_path: String,
    new_path: String,
}

#[derive(Deserialize)]
struct DeleteReq {
    paths: Vec<String>,
}

#[derive(Deserialize)]
struct BatchReq {
    sources: Vec<String>,
    destination_dir: String,
}

async fn write_json_response<W: AsyncWriteExt + Unpin>(
    writer: &mut W,
    status: &str,
    body: &str,
) -> std::io::Result<()> {
    let resp = format!(
        "HTTP/1.1 {}\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}",
        status, body.len(), body
    );
    writer.write_all(resp.as_bytes()).await
}

pub async fn handle_mut_request<R: AsyncReadExt + Unpin, W: AsyncWriteExt + Unpin>(
    route: &str,
    headers: &HashMap<String, String>,
    reader: &mut R,
    writer: &mut W,
) -> std::io::Result<()> {
    let content_len = headers.get("content-length")
        .and_then(|s| s.parse::<usize>().ok())
        .unwrap_or(0);
    let mut body = vec![0u8; content_len];
    if content_len > 0 {
        reader.read_exact(&mut body).await?;
    }

    let result = match route {
        "/api/fs/mkdir" => match serde_json::from_slice::<MkdirReq>(&body) {
            Ok(req) => dispatch_mkdir(&req.path).await,
            Err(e) => Err(std::io::Error::new(std::io::ErrorKind::InvalidInput, e)),
        },
        "/api/fs/rename" => match serde_json::from_slice::<RenameReq>(&body) {
            Ok(req) => dispatch_rename(&req.old_path, &req.new_path).await,
            Err(e) => Err(std::io::Error::new(std::io::ErrorKind::InvalidInput, e)),
        },
        "/api/fs/delete" => match serde_json::from_slice::<DeleteReq>(&body) {
            Ok(req) => dispatch_delete(&req.paths).await,
            Err(e) => Err(std::io::Error::new(std::io::ErrorKind::InvalidInput, e)),
        },
        "/api/fs/copy" => match serde_json::from_slice::<BatchReq>(&body) {
            Ok(req) => dispatch_batch(&req.sources, &req.destination_dir, false).await,
            Err(e) => Err(std::io::Error::new(std::io::ErrorKind::InvalidInput, e)),
        },
        "/api/fs/move" => match serde_json::from_slice::<BatchReq>(&body) {
            Ok(req) => dispatch_batch(&req.sources, &req.destination_dir, true).await,
            Err(e) => Err(std::io::Error::new(std::io::ErrorKind::InvalidInput, e)),
        },
        _ => Err(std::io::Error::new(std::io::ErrorKind::NotFound, "Route not found")),
    };

    match result {
        Ok(count) => {
            write_json_response(writer, "200 OK", &format!(r#"{{"success":true,"count":{}}}"#, count)).await
        }
        Err(e) => {
            let err = format!(r#"{{"error":"{}"}}"#, e);
            write_json_response(writer, "500 Internal Server Error", &err).await
        }
    }
}
