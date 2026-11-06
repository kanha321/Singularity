use std::collections::HashMap;
use tokio::io::AsyncWriteExt;
use tokio::net::tcp::WriteHalf;

pub async fn serve_media_art(
    params: &HashMap<String, String>,
    writer: &mut WriteHalf<'_>,
) -> std::io::Result<()> {
    let raw_path = match params.get("path") {
        Some(p) if !p.is_empty() => p,
        _ => {
            let resp = "HTTP/1.1 400 Bad Request\r\nContent-Length: 12\r\nConnection: close\r\n\r\nMissing path";
            writer.write_all(resp.as_bytes()).await?;
            return Ok(());
        }
    };

    let path = std::path::Path::new(raw_path);
    let bytes = match tokio::fs::read(path).await {
        Ok(b) => b,
        Err(_) => {
            let resp = "HTTP/1.1 404 Not Found\r\nContent-Length: 9\r\nConnection: close\r\n\r\nNot Found";
            writer.write_all(resp.as_bytes()).await?;
            return Ok(());
        }
    };

    let content_type = match path.extension().and_then(|e| e.to_str()).unwrap_or("") {
        "jpg" | "jpeg" => "image/jpeg",
        "png" => "image/png",
        "webp" => "image/webp",
        "svg" => "image/svg+xml",
        _ => "image/png",
    };

    let header = format!(
        "HTTP/1.1 200 OK\r\nContent-Type: {}\r\nContent-Length: {}\r\nCache-Control: public, max-age=3600\r\nConnection: close\r\n\r\n",
        content_type,
        bytes.len()
    );
    writer.write_all(header.as_bytes()).await?;
    writer.write_all(&bytes).await?;
    Ok(())
}

