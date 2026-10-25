use std::path::{Path, PathBuf};
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct FsEntry {
    pub name: String,
    pub path: String,
    pub is_dir: bool,
    pub size: u64,
    pub modified: u64,
    pub mime_type: String,
    pub is_symlink: bool,
    pub is_hidden: bool,
}

pub fn resolve_path(req_path: &str) -> PathBuf {
    let home = dirs::home_dir().unwrap_or_else(|| PathBuf::from("/home/kanhaji"));
    if req_path.is_empty() || req_path == "~" {
        return home;
    }
    if let Some(stripped) = req_path.strip_prefix("~/") {
        return home.join(stripped);
    }
    PathBuf::from(req_path)
}

pub fn guess_mime(path: &Path) -> &'static str {
    match path.extension().and_then(|e| e.to_str()).map(|e| e.to_ascii_lowercase()).as_deref() {
        Some("mp4") => "video/mp4",
        Some("mkv") => "video/x-matroska",
        Some("webm") => "video/webm",
        Some("avi") => "video/x-msvideo",
        Some("mov") => "video/quicktime",
        Some("png") => "image/png",
        Some("jpg") | Some("jpeg") => "image/jpeg",
        Some("webp") => "image/webp",
        Some("gif") => "image/gif",
        Some("svg") => "image/svg+xml",
        Some("pdf") => "application/pdf",
        Some("zip") => "application/zip",
        Some("tar") => "application/x-tar",
        Some("gz") => "application/gzip",
        Some("xz") => "application/x-xz",
        Some("7z") => "application/x-7z-compressed",
        Some("rar") => "application/vnd.rar",
        Some("mp3") => "audio/mpeg",
        Some("flac") => "audio/flac",
        Some("wav") => "audio/wav",
        Some("txt") | Some("md") | Some("rs") | Some("kt") | Some("json") | Some("toml") | Some("sh") | Some("py") => "text/plain",
        _ => "application/octet-stream",
    }
}

pub async fn list_directory(dir_path: &Path) -> std::io::Result<Vec<FsEntry>> {
    let mut read_dir = tokio::fs::read_dir(dir_path).await?;
    let mut entries = Vec::new();

    while let Ok(Some(entry)) = read_dir.next_entry().await {
        let meta = match entry.metadata().await {
            Ok(m) => m,
            Err(_) => continue,
        };

        let file_type = entry.file_type().await.ok();
        let is_dir = meta.is_dir();
        let is_symlink = file_type.map(|ft| ft.is_symlink()).unwrap_or(false);
        let name = entry.file_name().to_string_lossy().to_string();
        let is_hidden = name.starts_with('.');
        let path_buf = entry.path();
        let path_str = path_buf.to_string_lossy().to_string();

        let modified = meta.modified()
            .ok()
            .and_then(|t| t.duration_since(std::time::UNIX_EPOCH).ok())
            .map(|d| d.as_secs())
            .unwrap_or(0);

        let mime = if is_dir {
            "inode/directory".to_string()
        } else {
            guess_mime(&path_buf).to_string()
        };

        entries.push(FsEntry {
            name,
            path: path_str,
            is_dir,
            size: if is_dir { 0 } else { meta.len() },
            modified,
            mime_type: mime,
            is_symlink,
            is_hidden,
        });
    }

    entries.sort_by(|a, b| {
        match (a.is_dir, b.is_dir) {
            (true, false) => std::cmp::Ordering::Less,
            (false, true) => std::cmp::Ordering::Greater,
            _ => a.name.to_lowercase().cmp(&b.name.to_lowercase()),
        }
    });

    Ok(entries)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn resolve_path_handles_home_tilde_and_absolute() {
        let home = dirs::home_dir().unwrap_or_else(|| PathBuf::from("/home/kanhaji"));
        assert_eq!(resolve_path(""), home);
        assert_eq!(resolve_path("~"), home);
        assert_eq!(resolve_path("~/Downloads"), home.join("Downloads"));
        assert_eq!(resolve_path("/etc"), PathBuf::from("/etc"));
    }

    #[test]
    fn guess_mime_classifies_media_types() {
        assert_eq!(guess_mime(Path::new("movie.mkv")), "video/x-matroska");
        assert_eq!(guess_mime(Path::new("pic.png")), "image/png");
        assert_eq!(guess_mime(Path::new("doc.pdf")), "application/pdf");
        assert_eq!(guess_mime(Path::new("archive.zip")), "application/zip");
    }
}
