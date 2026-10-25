use std::io::SeekFrom;
use std::path::Path;
use tokio::io::{AsyncReadExt, AsyncSeekExt, AsyncWriteExt};

pub fn parse_range(range_header: &str, file_len: u64) -> Option<(u64, u64)> {
    let s = range_header.strip_prefix("bytes=")?;
    let mut parts = s.split('-');
    let start_str = parts.next()?.trim();
    let end_str = parts.next()?.trim();

    if file_len == 0 {
        return None;
    }

    if start_str.is_empty() {
        let suffix: u64 = end_str.parse().ok()?;
        let start = file_len.saturating_sub(suffix);
        Some((start, file_len - 1))
    } else {
        let start: u64 = start_str.parse().ok()?;
        if start >= file_len {
            return None;
        }
        let end = if end_str.is_empty() {
            file_len - 1
        } else {
            let end_val: u64 = end_str.parse().ok()?;
            end_val.min(file_len - 1)
        };
        if start > end {
            return None;
        }
        Some((start, end))
    }
}

pub async fn stream_file_response<W: AsyncWriteExt + Unpin>(
    file_path: &Path,
    range_header: Option<&str>,
    writer: &mut W,
) -> std::io::Result<()> {
    let mut file = tokio::fs::File::open(file_path).await?;
    let metadata = file.metadata().await?;
    let file_len = metadata.len();
    let mime = super::fs_ops::guess_mime(file_path);

    if let Some(range_raw) = range_header {
        if let Some((start, end)) = parse_range(range_raw, file_len) {
            let chunk_len = end - start + 1;
            let header = format!(
                "HTTP/1.1 206 Partial Content\r\nContent-Type: {}\r\nContent-Length: {}\r\nContent-Range: bytes {}-{}/{}\r\nAccept-Ranges: bytes\r\nConnection: close\r\n\r\n",
                mime, chunk_len, start, end, file_len
            );
            writer.write_all(header.as_bytes()).await?;

            file.seek(SeekFrom::Start(start)).await?;
            let mut take = file.take(chunk_len);
            tokio::io::copy(&mut take, writer).await?;
            writer.flush().await?;
            return Ok(());
        }
    }

    let header = format!(
        "HTTP/1.1 200 OK\r\nContent-Type: {}\r\nContent-Length: {}\r\nAccept-Ranges: bytes\r\nConnection: close\r\n\r\n",
        mime, file_len
    );
    writer.write_all(header.as_bytes()).await?;
    tokio::io::copy(&mut file, writer).await?;
    writer.flush().await?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_parse_range_variations() {
        assert_eq!(parse_range("bytes=0-499", 1000), Some((0, 499)));
        assert_eq!(parse_range("bytes=500-", 1000), Some((500, 999)));
        assert_eq!(parse_range("bytes=-200", 1000), Some((800, 999)));
        assert_eq!(parse_range("bytes=1500-", 1000), None);
    }
}
