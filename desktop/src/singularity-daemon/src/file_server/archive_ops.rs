use serde::{Deserialize, Serialize};
use std::io;
use std::path::{Path, PathBuf};
use tokio::process::Command;

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct ArchiveEntry {
    pub path: String,
    pub is_dir: bool,
    pub size: u64,
    pub packed_size: u64,
    pub modified: String,
}

pub fn parse_7z_slt_output(output: &str) -> Vec<ArchiveEntry> {
    let mut entries = Vec::new();
    let mut current_path: Option<String> = None;
    let mut is_dir = false;
    let mut size = 0u64;
    let mut packed_size = 0u64;
    let mut modified = String::new();

    let mut in_entries = false;

    for line in output.lines() {
        let trimmed = line.trim();
        if trimmed.starts_with("----------") { in_entries = true; continue; }
        if !in_entries { continue; }
        if trimmed.is_empty() {
            if let Some(p) = current_path.take() {
                entries.push(ArchiveEntry { path: p, is_dir, size, packed_size, modified: modified.clone() });
                is_dir = false; size = 0; packed_size = 0; modified.clear();
            }
            continue;
        }
        if let Some((k, v)) = trimmed.split_once('=') {
            match k.trim() {
                "Path" => current_path = Some(v.trim().replace('\\', "/")),
                "Folder" => is_dir = v.trim() == "+",
                "Size" => size = v.trim().parse().unwrap_or(0),
                "Packed Size" => packed_size = v.trim().parse().unwrap_or(0),
                "Modified" => modified = v.trim().to_string(),
                _ => {}
            }
        }
    }

    if let Some(p) = current_path.take() {
        entries.push(ArchiveEntry { path: p, is_dir, size, packed_size, modified });
    }
    entries
}

pub async fn list_archive(path: &Path) -> io::Result<Vec<ArchiveEntry>> {
    let output = Command::new("7z")
        .arg("l")
        .arg("-slt")
        .arg(path)
        .output()
        .await?;

    if !output.status.success() {
        let err = String::from_utf8_lossy(&output.stderr);
        return Err(io::Error::new(io::ErrorKind::Other, err.to_string()));
    }

    let stdout = String::from_utf8_lossy(&output.stdout);
    Ok(parse_7z_slt_output(&stdout))
}

pub async fn extract_archive(archive_path: &Path, destination_dir: &Path) -> io::Result<()> {
    let out_flag = format!("-o{}", destination_dir.display());
    let output = Command::new("7z")
        .arg("x")
        .arg(archive_path)
        .arg(&out_flag)
        .arg("-y")
        .output()
        .await?;

    if !output.status.success() {
        let err = String::from_utf8_lossy(&output.stderr);
        return Err(io::Error::new(io::ErrorKind::Other, err.to_string()));
    }
    Ok(())
}

pub async fn compress_archive(sources: &[PathBuf], archive_path: &Path) -> io::Result<()> {
    let mut cmd = Command::new("7z");
    cmd.arg("a").arg(archive_path);
    for src in sources {
        cmd.arg(src);
    }
    let output = cmd.output().await?;
    if !output.status.success() {
        let err = String::from_utf8_lossy(&output.stderr);
        return Err(io::Error::new(io::ErrorKind::Other, err.to_string()));
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_parse_7z_slt_output() {
        let sample = r#"
Listing archive: /tmp/test.zip
--
Path = /tmp/test.zip
Type = zip

----------
Path = src/main.rs
Folder = -
Size = 1024
Packed Size = 512
Modified = 2026-10-25 18:00:00

Path = src
Folder = +
Size = 0
Packed Size = 0
Modified = 2026-10-25 17:59:00
"#;
        let entries = parse_7z_slt_output(sample);
        assert_eq!(entries.len(), 2);
        assert_eq!(entries[0].path, "src/main.rs");
        assert!(!entries[0].is_dir);
        assert_eq!(entries[0].size, 1024);
        assert_eq!(entries[0].packed_size, 512);

        assert_eq!(entries[1].path, "src");
        assert!(entries[1].is_dir);
        assert_eq!(entries[1].size, 0);
    }
}
