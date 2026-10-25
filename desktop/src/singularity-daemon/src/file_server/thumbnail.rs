use std::collections::hash_map::DefaultHasher;
use std::hash::{Hash, Hasher};
use std::path::{Path, PathBuf};
use tokio::io::AsyncWriteExt;
use tokio::net::tcp::WriteHalf;
use tokio::process::Command;

pub fn is_previewable(path: &Path) -> Option<bool> {
    let ext = path.extension()?.to_str()?.to_ascii_lowercase();
    match ext.as_str() {
        "jpg" | "jpeg" | "png" | "webp" | "gif" | "bmp" | "svg" | "ico" | "avif" => Some(false),
        "mp4" | "mkv" | "avi" | "mov" | "webm" | "flv" | "wmv" | "m4v" | "ts" => Some(true),
        _ => None,
    }
}

pub fn get_cache_path(path: &Path, mtime: u64, size: u64) -> PathBuf {
    let mut hasher = DefaultHasher::new();
    path.to_string_lossy().hash(&mut hasher);
    mtime.hash(&mut hasher);
    size.hash(&mut hasher);
    let hash = hasher.finish();

    let cache_dir = dirs::cache_dir()
        .unwrap_or_else(|| PathBuf::from("/tmp"))
        .join("singularity")
        .join("thumbnails");
    let _ = std::fs::create_dir_all(&cache_dir);
    cache_dir.join(format!("{:016x}.jpg", hash))
}

pub async fn generate_thumbnail(source: &Path, dest: &Path, is_video: bool) -> Result<(), String> {
    let mut cmd = Command::new("ffmpeg");
    cmd.arg("-y");
    if is_video {
        cmd.args(["-ss", "00:00:01", "-i"]).arg(source);
        cmd.args(["-vframes", "1"]);
    } else {
        cmd.args(["-i"]).arg(source);
    }
    cmd.args([
        "-vf",
        "scale=128:128:force_original_aspect_ratio=decrease",
        "-q:v",
        "3",
    ])
    .arg(dest);

    let output = cmd.output().await.map_err(|e| format!("ffmpeg execution failed: {}", e))?;
    if !output.status.success() {
        return Err(String::from_utf8_lossy(&output.stderr).to_string());
    }
    Ok(())
}

pub async fn serve_thumbnail(req_path: &str, writer: &mut WriteHalf<'_>) -> std::io::Result<()> {
    let target = super::fs_ops::resolve_path(req_path);
    let is_video = match is_previewable(&target) {
        Some(v) => v,
        None => {
            let resp = "HTTP/1.1 415 Unsupported Media Type\r\nContent-Length: 0\r\nConnection: close\r\n\r\n";
            writer.write_all(resp.as_bytes()).await?;
            return Ok(());
        }
    };

    let meta = match tokio::fs::metadata(&target).await {
        Ok(m) => m,
        Err(_) => {
            let resp = "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n";
            writer.write_all(resp.as_bytes()).await?;
            return Ok(());
        }
    };

    let mtime = meta.modified().ok()
        .and_then(|t| t.duration_since(std::time::UNIX_EPOCH).ok())
        .map(|d| d.as_secs())
        .unwrap_or(0);
    let cache_file = get_cache_path(&target, mtime, meta.len());

    if !cache_file.exists() {
        if let Err(e) = generate_thumbnail(&target, &cache_file, is_video).await {
            let err = format!(r#"{{"error":"{}"}}"#, e);
            let resp = format!("HTTP/1.1 500 Internal Error\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}", err.len(), err);
            writer.write_all(resp.as_bytes()).await?;
            return Ok(());
        }
    }

    match tokio::fs::read(&cache_file).await {
        Ok(bytes) => {
            let header = format!(
                "HTTP/1.1 200 OK\r\nContent-Type: image/jpeg\r\nContent-Length: {}\r\nCache-Control: public, max-age=604800\r\nConnection: close\r\n\r\n",
                bytes.len()
            );
            writer.write_all(header.as_bytes()).await?;
            writer.write_all(&bytes).await?;
        }
        Err(_) => {
            let resp = "HTTP/1.1 500 Internal Error\r\nContent-Length: 0\r\nConnection: close\r\n\r\n";
            writer.write_all(resp.as_bytes()).await?;
        }
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_is_previewable() {
        assert_eq!(is_previewable(Path::new("photo.JPG")), Some(false));
        assert_eq!(is_previewable(Path::new("clip.mp4")), Some(true));
        assert_eq!(is_previewable(Path::new("doc.pdf")), None);
        assert_eq!(is_previewable(Path::new("binary.bin")), None);
    }

    #[test]
    fn test_cache_path_deterministic() {
        let p1 = get_cache_path(Path::new("/tmp/test.png"), 100, 500);
        let p2 = get_cache_path(Path::new("/tmp/test.png"), 100, 500);
        let p3 = get_cache_path(Path::new("/tmp/test.png"), 101, 500);
        assert_eq!(p1, p2);
        assert_ne!(p1, p3);
    }
}
