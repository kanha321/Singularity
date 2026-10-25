use std::collections::HashMap;
use std::path::PathBuf;
use serde::Deserialize;
use tokio::io::{AsyncReadExt, AsyncWriteExt};
use super::archive_ops::{compress_archive, extract_archive, list_archive};
use super::archive_stream::stream_archive_file;
use super::fs_ops::resolve_path;

#[derive(Deserialize)]
struct ExtractReq {
    path: String,
    destination_dir: String,
}

#[derive(Deserialize)]
struct CompressReq {
    sources: Vec<String>,
    archive_path: String,
}

async fn write_json<W: AsyncWriteExt + Unpin>(
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

pub async fn handle_archive_get<W: AsyncWriteExt + Unpin>(
    route: &str,
    params: &HashMap<String, String>,
    writer: &mut W,
) -> std::io::Result<()> {
    let raw_path = params.get("path").map(|s| s.as_str()).unwrap_or("");
    let target = resolve_path(raw_path);

    match route {
        "/api/fs/archive/list" => match list_archive(&target).await {
            Ok(entries) => {
                let json = serde_json::to_string(&entries).unwrap_or_else(|_| "[]".into());
                write_json(writer, "200 OK", &json).await
            }
            Err(e) => {
                let err = format!(r#"{{"error":"{}"}}"#, e);
                write_json(writer, "500 Internal Server Error", &err).await
            }
        },
        "/api/fs/archive/raw" => {
            let entry = params.get("entry").map(|s| s.as_str()).unwrap_or("");
            if let Err(e) = stream_archive_file(&target, entry, writer).await {
                let err = format!(r#"{{"error":"{}"}}"#, e);
                write_json(writer, "404 Not Found", &err).await?;
            }
            Ok(())
        }
        _ => write_json(writer, "404 Not Found", r#"{"error":"Not Found"}"#).await,
    }
}

pub async fn handle_archive_post<R: AsyncReadExt + Unpin, W: AsyncWriteExt + Unpin>(
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
        "/api/fs/archive/extract" => match serde_json::from_slice::<ExtractReq>(&body) {
            Ok(req) => extract_archive(&resolve_path(&req.path), &resolve_path(&req.destination_dir)).await,
            Err(e) => Err(std::io::Error::new(std::io::ErrorKind::InvalidInput, e)),
        },
        "/api/fs/archive/compress" => match serde_json::from_slice::<CompressReq>(&body) {
            Ok(req) => {
                let sources: Vec<PathBuf> = req.sources.iter().map(|s| resolve_path(s)).collect();
                compress_archive(&sources, &resolve_path(&req.archive_path)).await
            }
            Err(e) => Err(std::io::Error::new(std::io::ErrorKind::InvalidInput, e)),
        },
        _ => Err(std::io::Error::new(std::io::ErrorKind::NotFound, "Unknown route")),
    };

    match result {
        Ok(_) => write_json(writer, "200 OK", r#"{"success":true}"#).await,
        Err(e) => {
            let err = format!(r#"{{"error":"{}"}}"#, e);
            write_json(writer, "500 Internal Server Error", &err).await
        }
    }
}
