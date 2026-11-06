use std::collections::HashMap;
use std::sync::Arc;
use tokio::io::{AsyncBufReadExt, AsyncWriteExt, BufReader};
use tokio::net::TcpStream;
use tokio::sync::RwLock;
use super::fs_ops::{list_directory, resolve_path};
use super::stream::stream_file_response;
#[allow(unused_imports)]
pub use super::query::{parse_query, url_decode};

pub async fn handle_http_connection(
    mut stream: TcpStream,
    active_device: Arc<RwLock<String>>,
) -> std::io::Result<()> {
    let (read_half, mut write_half) = stream.split();
    let mut reader = BufReader::new(read_half);
    let mut request_line = String::new();
    if reader.read_line(&mut request_line).await? == 0 {
        return Ok(());
    }

    let parts: Vec<&str> = request_line.split_whitespace().collect();
    if parts.len() < 2 {
        return Ok(());
    }
    let method = parts[0];
    let full_path = parts[1];

    let mut headers = HashMap::new();
    loop {
        let mut line = String::new();
        let bytes = reader.read_line(&mut line).await?;
        if bytes == 0 || line == "\r\n" || line == "\n" {
            break;
        }
        if let Some((k, v)) = line.split_once(':') {
            headers.insert(k.trim().to_ascii_lowercase(), v.trim().to_string());
        }
    }

    let (route, query) = full_path.split_once('?').unwrap_or((full_path, ""));
    let params = parse_query(query);

    match (method, route) {
        ("GET", "/health") | ("GET", "/api/health") => {
            let resp = "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: 2\r\nConnection: close\r\n\r\nOK";
            write_half.write_all(resp.as_bytes()).await?;
        }
        ("GET", "/api/fs/list") => {
            let req_path = params.get("path").map(|s| s.as_str()).unwrap_or("");
            let target = resolve_path(req_path);
            match list_directory(&target).await {
                Ok(entries) => {
                    let json = serde_json::to_string(&entries).unwrap_or_else(|_| "[]".into());
                    let resp = format!(
                        "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}",
                        json.len(), json
                    );
                    write_half.write_all(resp.as_bytes()).await?;
                }
                Err(e) => {
                    let err = format!(r#"{{"error":"{}"}}"#, e);
                    let resp = format!(
                        "HTTP/1.1 500 Internal Server Error\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}",
                        err.len(), err
                    );
                    write_half.write_all(resp.as_bytes()).await?;
                }
            }
        }
        ("GET", "/api/fs/raw") => {
            let req_path = params.get("path").map(|s| s.as_str()).unwrap_or("");
            let target = resolve_path(req_path);
            let range = headers.get("range").map(|s| s.as_str());
            if let Err(e) = stream_file_response(&target, range, &mut write_half).await {
                let err = format!(r#"{{"error":"{}"}}"#, e);
                let resp = format!("HTTP/1.1 404 Not Found\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}", err.len(), err);
                let _ = write_half.write_all(resp.as_bytes()).await;
            }
        }
        ("GET", "/api/fs/folder_size") => {
            let req_path = params.get("path").map(|s| s.as_str()).unwrap_or("");
            let target = resolve_path(req_path);
            let _ = super::folder_size::stream_folder_size(&target, &mut write_half).await;
        }
        ("GET", "/api/fs/thumbnail") => {
            let req_path = params.get("path").map(|s| s.as_str()).unwrap_or("");
            super::thumbnail::serve_thumbnail(req_path, &mut write_half).await?;
        }
        ("GET", "/api/media/art") => {
            super::media_router::serve_media_art(&params, &mut write_half).await?;
        }
        ("POST", "/api/fs/mkdir")
        | ("POST", "/api/fs/rename")
        | ("POST", "/api/fs/delete")
        | ("POST", "/api/fs/copy")
        | ("POST", "/api/fs/move") => {
            super::mut_router::handle_mut_request(route, &headers, &mut reader, &mut write_half).await?;
        }
        ("GET", "/api/fs/archive/list") | ("GET", "/api/fs/archive/raw") => {
            super::archive_router::handle_archive_get(route, &params, &mut write_half).await?;
        }
        ("POST", "/api/fs/archive/extract") | ("POST", "/api/fs/archive/compress") => {
            super::archive_router::handle_archive_post(route, &headers, &mut reader, &mut write_half).await?;
        }
        ("OPTIONS", "/api/browser/open") | ("OPTIONS", "/api/desktop/open") => {
            super::browser_router::handle_browser_options(&mut write_half).await?;
        }
        ("POST", "/api/browser/open") => {
            super::browser_router::handle_browser_post(&headers, &mut reader, &mut write_half, &active_device).await?;
        }
        ("POST", "/api/desktop/open") => {
            super::browser_router::handle_desktop_open_post(&headers, &mut reader, &mut write_half).await?;
        }
        _ => {
            let resp = "HTTP/1.1 404 Not Found\r\nContent-Length: 9\r\nConnection: close\r\n\r\nNot Found";
            write_half.write_all(resp.as_bytes()).await?;
        }
    }
    write_half.flush().await?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_url_decode_and_query_parser() {
        assert_eq!(url_decode("hello%20world%21"), "hello world!");
        let params = parse_query("path=%2Fhome%2Fkanhaji%2FVideos&sort=name");
        assert_eq!(params.get("path"), Some(&"/home/kanhaji/Videos".to_string()));
        assert_eq!(params.get("sort"), Some(&"name".to_string()));
    }
}
