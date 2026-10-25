use std::path::Path;
use std::time::{Duration, Instant};
use tokio::io::{AsyncWrite, AsyncWriteExt};

async fn write_chunk<W: AsyncWrite + Unpin>(writer: &mut W, val: &serde_json::Value) -> std::io::Result<()> {
    let mut line = serde_json::to_string(val).unwrap_or_default();
    line.push('\n');
    let chunk = format!("{:X}\r\n{}\r\n", line.len(), line);
    writer.write_all(chunk.as_bytes()).await?;
    writer.flush().await
}

pub async fn stream_folder_size<W: AsyncWrite + Unpin>(path: &Path, writer: &mut W) -> std::io::Result<()> {
    let header = "HTTP/1.1 200 OK\r\nContent-Type: application/x-ndjson\r\nTransfer-Encoding: chunked\r\nCache-Control: no-cache\r\nConnection: close\r\n\r\n";
    writer.write_all(header.as_bytes()).await?;

    if !path.is_dir() {
        write_chunk(writer, &serde_json::json!({
            "bytes": 0, "files": 0, "folders": 0, "done": true, "error": "Not a directory"
        })).await?;
        writer.write_all(b"0\r\n\r\n").await?;
        return writer.flush().await;
    }

    write_chunk(writer, &serde_json::json!({
        "bytes": 0, "files": 0, "folders": 0, "done": false
    })).await?;

    let mut stack = vec![path.to_path_buf()];
    let mut total_bytes: u64 = 0;
    let mut total_files: u64 = 0;
    let mut total_folders: u64 = 0;
    let mut last_emit = Instant::now();

    while let Some(current) = stack.pop() {
        let mut read_dir = match tokio::fs::read_dir(&current).await {
            Ok(rd) => rd,
            Err(_) => continue,
        };

        while let Ok(Some(entry)) = read_dir.next_entry().await {
            let ft = match entry.file_type().await {
                Ok(ft) => ft,
                Err(_) => continue,
            };

            if ft.is_dir() && !ft.is_symlink() {
                total_folders += 1;
                stack.push(entry.path());
            } else {
                total_files += 1;
                if let Ok(meta) = entry.metadata().await {
                    total_bytes += meta.len();
                }
            }

            if last_emit.elapsed() >= Duration::from_millis(75) {
                write_chunk(writer, &serde_json::json!({
                    "bytes": total_bytes, "files": total_files, "folders": total_folders, "done": false
                })).await?;
                last_emit = Instant::now();
            }
        }
    }

    write_chunk(writer, &serde_json::json!({
        "bytes": total_bytes, "files": total_files, "folders": total_folders, "done": true
    })).await?;
    writer.write_all(b"0\r\n\r\n").await?;
    writer.flush().await
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn test_stream_folder_size_nonexistent() {
        let mut buf = Vec::new();
        let res = stream_folder_size(Path::new("/nonexistent_folder_xyz_99"), &mut buf).await;
        assert!(res.is_ok());
        let str_out = String::from_utf8_lossy(&buf);
        assert!(str_out.contains("Not a directory"));
        assert!(str_out.contains("\"done\":true"));
    }

    #[tokio::test]
    async fn test_stream_folder_size_valid_dir() {
        let temp = std::env::temp_dir().join("singularity_test_folder_size");
        let _ = tokio::fs::create_dir_all(&temp).await;
        let file_path = temp.join("test.txt");
        let _ = tokio::fs::write(&file_path, b"hello world 123").await;

        let mut buf = Vec::new();
        let res = stream_folder_size(&temp, &mut buf).await;
        assert!(res.is_ok());

        let str_out = String::from_utf8_lossy(&buf);
        assert!(str_out.contains("\"files\":1"));
        assert!(str_out.contains("\"done\":true"));

        let _ = tokio::fs::remove_dir_all(&temp).await;
    }
}
