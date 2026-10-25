use std::io;
use std::path::{Path, PathBuf};
use tokio::process::Command;
use super::fs_ops::resolve_path;

pub fn parse_virtual_path(path: &str) -> Option<(PathBuf, String)> {
    let sep = if path.contains("!/") { "!/" } else if path.contains('!') { "!" } else { return None; };
    let (archive, internal) = path.split_once(sep)?;
    Some((resolve_path(archive), internal.trim_matches('/').to_string()))
}

pub async fn delete_archive_entries(archive: &Path, internal_paths: &[String]) -> io::Result<usize> {
    if internal_paths.is_empty() { return Ok(0); }
    let mut cmd = Command::new("7z");
    cmd.arg("d").arg(archive);
    for p in internal_paths { cmd.arg(p); }
    let out = cmd.output().await?;
    if !out.status.success() {
        return Err(io::Error::new(io::ErrorKind::Other, String::from_utf8_lossy(&out.stderr).to_string()));
    }
    Ok(internal_paths.len())
}

pub async fn rename_archive_entry(archive: &Path, old_p: &str, new_p: &str) -> io::Result<()> {
    let out = Command::new("7z").arg("rn").arg(archive).arg(old_p).arg(new_p).output().await?;
    if !out.status.success() {
        return Err(io::Error::new(io::ErrorKind::Other, String::from_utf8_lossy(&out.stderr).to_string()));
    }
    Ok(())
}

pub async fn create_archive_folder(archive: &Path, internal_dir: &str) -> io::Result<()> {
    let clean = internal_dir.trim_matches('/');
    if clean.is_empty() { return Ok(()); }
    let tmp = tempfile::tempdir()?;
    let leaf = clean.rsplit('/').next().unwrap_or(clean);
    let stage = tmp.path().join(leaf);
    tokio::fs::create_dir_all(&stage).await?;

    let out = Command::new("7z").arg("a").arg(archive).arg(leaf).current_dir(tmp.path()).output().await?;
    if !out.status.success() {
        return Err(io::Error::new(io::ErrorKind::Other, String::from_utf8_lossy(&out.stderr).to_string()));
    }
    if leaf != clean {
        rename_archive_entry(archive, leaf, clean).await?;
    }
    Ok(())
}

pub async fn extract_entries_to(archive: &Path, internal_paths: &[String], dest_dir: &Path) -> io::Result<usize> {
    tokio::fs::create_dir_all(dest_dir).await?;
    let tmp = tempfile::tempdir()?;
    let out_flag = format!("-o{}", tmp.path().display());
    let mut cmd = Command::new("7z");
    cmd.arg("x").arg(archive).arg(&out_flag).arg("-y");
    for p in internal_paths { cmd.arg(p); }
    let out = cmd.output().await?;
    if !out.status.success() {
        return Err(io::Error::new(io::ErrorKind::Other, String::from_utf8_lossy(&out.stderr).to_string()));
    }
    for p in internal_paths {
        let src = tmp.path().join(p);
        let name = Path::new(p).file_name().unwrap_or_default();
        let dst = dest_dir.join(name);
        if src.is_dir() {
            super::mut_ops::copy_entry(&src, &dst).await?;
        } else if src.exists() {
            tokio::fs::copy(&src, &dst).await?;
        }
    }
    Ok(internal_paths.len())
}

pub async fn add_paths_to_archive(sources: &[PathBuf], archive: &Path, internal_dest: &str) -> io::Result<usize> {
    let clean_dest = internal_dest.trim_matches('/');
    let mut count = 0;
    for src in sources {
        let name = src.file_name().and_then(|n| n.to_str()).unwrap_or_default();
        let parent = src.parent().unwrap_or_else(|| Path::new("."));
        let out = Command::new("7z").arg("a").arg(archive).arg(name).current_dir(parent).output().await?;
        if !out.status.success() {
            return Err(io::Error::new(io::ErrorKind::Other, String::from_utf8_lossy(&out.stderr).to_string()));
        }
        if !clean_dest.is_empty() {
            let target = format!("{}/{}", clean_dest, name);
            let _ = rename_archive_entry(archive, name, &target).await;
        }
        count += 1;
    }
    Ok(count)
}

#[cfg(test)]
mod tests {
    use super::*;
    use tempfile::tempdir;

    #[test]
    fn test_parse_virtual_path() {
        let res = parse_virtual_path("/tmp/test.zip!/src/main.rs");
        assert!(res.is_some());
        let (arch, entry) = res.unwrap();
        assert!(arch.ends_with("test.zip"));
        assert_eq!(entry, "src/main.rs");

        let res2 = parse_virtual_path("/tmp/test.zip!/");
        assert!(res2.is_some());
        assert_eq!(res2.unwrap().1, "");

        assert!(parse_virtual_path("/tmp/normal.txt").is_none());
    }

    #[tokio::test]
    async fn test_archive_mut_lifecycle() {
        let tmp = tempdir().unwrap();
        let arch = tmp.path().join("test.zip");
        let f = tmp.path().join("sample.txt");
        tokio::fs::write(&f, b"content").await.unwrap();

        add_paths_to_archive(&[f], &arch, "").await.unwrap();
        rename_archive_entry(&arch, "sample.txt", "renamed.txt").await.unwrap();
        create_archive_folder(&arch, "docs").await.unwrap();

        let out_dir = tmp.path().join("extracted");
        let count = extract_entries_to(&arch, &["renamed.txt".to_string()], &out_dir).await.unwrap();
        assert_eq!(count, 1);
        assert!(out_dir.join("renamed.txt").exists());

        let del_count = delete_archive_entries(&arch, &["renamed.txt".to_string()]).await.unwrap();
        assert_eq!(del_count, 1);
    }
}
