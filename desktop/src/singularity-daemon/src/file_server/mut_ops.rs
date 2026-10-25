use std::path::{Path, PathBuf};

pub async fn create_directory(path: &Path) -> std::io::Result<()> {
    tokio::fs::create_dir_all(path).await
}

pub async fn rename_path(old_path: &Path, new_path: &Path) -> std::io::Result<()> {
    tokio::fs::rename(old_path, new_path).await
}

pub async fn delete_paths(paths: &[PathBuf]) -> std::io::Result<usize> {
    let mut count = 0;
    for path in paths {
        if path.is_dir() {
            tokio::fs::remove_dir_all(path).await?;
            count += 1;
        } else if path.exists() || path.is_symlink() {
            tokio::fs::remove_file(path).await?;
            count += 1;
        }
    }
    Ok(count)
}

pub async fn copy_entry(src: &Path, dst: &Path) -> std::io::Result<()> {
    if !src.is_dir() {
        tokio::fs::copy(src, dst).await?;
        return Ok(());
    }
    tokio::fs::create_dir_all(dst).await?;
    let mut stack = vec![(src.to_path_buf(), dst.to_path_buf())];
    while let Some((curr_src, curr_dst)) = stack.pop() {
        let mut read_dir = tokio::fs::read_dir(&curr_src).await?;
        while let Some(entry) = read_dir.next_entry().await? {
            let entry_path = entry.path();
            let dest_child = curr_dst.join(entry.file_name());
            let file_type = entry.file_type().await?;
            if file_type.is_dir() {
                tokio::fs::create_dir_all(&dest_child).await?;
                stack.push((entry_path, dest_child));
            } else {
                tokio::fs::copy(&entry_path, &dest_child).await?;
            }
        }
    }
    Ok(())
}

pub async fn copy_paths(sources: &[PathBuf], dest_dir: &Path) -> std::io::Result<usize> {
    tokio::fs::create_dir_all(dest_dir).await?;
    let mut count = 0;
    for src in sources {
        let name = src.file_name().ok_or_else(|| {
            std::io::Error::new(std::io::ErrorKind::InvalidInput, "Invalid source filename")
        })?;
        let target = dest_dir.join(name);
        copy_entry(src, &target).await?;
        count += 1;
    }
    Ok(count)
}

pub async fn move_paths(sources: &[PathBuf], dest_dir: &Path) -> std::io::Result<usize> {
    tokio::fs::create_dir_all(dest_dir).await?;
    let mut count = 0;
    for src in sources {
        let name = src.file_name().ok_or_else(|| {
            std::io::Error::new(std::io::ErrorKind::InvalidInput, "Invalid source filename")
        })?;
        let target = dest_dir.join(name);
        if tokio::fs::rename(src, &target).await.is_err() {
            copy_entry(src, &target).await?;
            if src.is_dir() {
                tokio::fs::remove_dir_all(src).await?;
            } else {
                tokio::fs::remove_file(src).await?;
            }
        }
        count += 1;
    }
    Ok(count)
}

#[cfg(test)]
mod tests {
    use super::*;
    use tempfile::tempdir;

    #[tokio::test]
    async fn test_create_and_delete_directory() {
        let tmp = tempdir().unwrap();
        let target = tmp.path().join("sub/folder");
        create_directory(&target).await.unwrap();
        assert!(target.is_dir());

        let count = delete_paths(&[target]).await.unwrap();
        assert_eq!(count, 1);
    }

    #[tokio::test]
    async fn test_rename_and_copy_and_move() {
        let tmp = tempdir().unwrap();
        let f1 = tmp.path().join("file1.txt");
        tokio::fs::write(&f1, b"hello").await.unwrap();

        let f2 = tmp.path().join("file2.txt");
        rename_path(&f1, &f2).await.unwrap();
        assert!(!f1.exists());
        assert!(f2.exists());

        let dest = tmp.path().join("dest");
        copy_paths(&[f2.clone()], &dest).await.unwrap();
        assert!(dest.join("file2.txt").exists());

        let dest2 = tmp.path().join("dest2");
        move_paths(&[f2], &dest2).await.unwrap();
        assert!(dest2.join("file2.txt").exists());
    }
}
