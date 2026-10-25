use std::collections::HashMap;
use std::io;
use std::path::{Path, PathBuf};
use super::fs_ops::resolve_path;
use super::mut_ops::{copy_paths, create_directory, delete_paths, move_paths, rename_path};
use super::archive_mut::{
    add_paths_to_archive, create_archive_folder, delete_archive_entries,
    extract_entries_to, parse_virtual_path, rename_archive_entry,
};

pub async fn dispatch_mkdir(path: &str) -> io::Result<usize> {
    if let Some((arch, internal)) = parse_virtual_path(path) {
        create_archive_folder(&arch, &internal).await?;
        Ok(1)
    } else {
        create_directory(&resolve_path(path)).await?;
        Ok(1)
    }
}

pub async fn dispatch_rename(old_p: &str, new_p: &str) -> io::Result<usize> {
    if let (Some((a1, i1)), Some((a2, i2))) = (parse_virtual_path(old_p), parse_virtual_path(new_p)) {
        if a1 == a2 {
            rename_archive_entry(&a1, &i1, &i2).await?;
            return Ok(1);
        }
    }
    rename_path(&resolve_path(old_p), &resolve_path(new_p)).await?;
    Ok(1)
}

pub async fn dispatch_delete(paths: &[String]) -> io::Result<usize> {
    let mut real_paths = Vec::new();
    let mut archive_map: HashMap<PathBuf, Vec<String>> = HashMap::new();

    for p in paths {
        if let Some((arch, internal)) = parse_virtual_path(p) {
            archive_map.entry(arch).or_default().push(internal);
        } else {
            real_paths.push(resolve_path(p));
        }
    }

    let mut total = 0;
    if !real_paths.is_empty() {
        total += delete_paths(&real_paths).await?;
    }
    for (arch, internals) in archive_map {
        total += delete_archive_entries(&arch, &internals).await?;
    }
    Ok(total)
}

pub async fn dispatch_batch(sources: &[String], destination: &str, is_move: bool) -> io::Result<usize> {
    if let Some((dst_arch, dst_internal)) = parse_virtual_path(destination) {
        let mut total = 0;
        let tmp = tempfile::tempdir()?;
        for src_str in sources {
            if let Some((src_arch, src_int)) = parse_virtual_path(src_str) {
                extract_entries_to(&src_arch, &[src_int.clone()], tmp.path()).await?;
                let name = Path::new(&src_int).file_name().unwrap_or_default();
                let extracted_file = tmp.path().join(name);
                add_paths_to_archive(&[extracted_file], &dst_arch, &dst_internal).await?;
                if is_move {
                    delete_archive_entries(&src_arch, &[src_int]).await?;
                }
            } else {
                let resolved = resolve_path(src_str);
                add_paths_to_archive(&[resolved.clone()], &dst_arch, &dst_internal).await?;
                if is_move {
                    delete_paths(&[resolved]).await?;
                }
            }
            total += 1;
        }
        Ok(total)
    } else {
        let dest_resolved = resolve_path(destination);
        let mut total = 0;
        let mut real_sources = Vec::new();

        for src_str in sources {
            if let Some((src_arch, src_int)) = parse_virtual_path(src_str) {
                extract_entries_to(&src_arch, &[src_int.clone()], &dest_resolved).await?;
                if is_move {
                    delete_archive_entries(&src_arch, &[src_int]).await?;
                }
                total += 1;
            } else {
                real_sources.push(resolve_path(src_str));
            }
        }
        if !real_sources.is_empty() {
            total += if is_move {
                move_paths(&real_sources, &dest_resolved).await?
            } else {
                copy_paths(&real_sources, &dest_resolved).await?
            };
        }
        Ok(total)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use tempfile::tempdir;

    #[tokio::test]
    async fn test_dispatch_cross_boundary_flow() {
        let tmp = tempdir().unwrap();
        let arch = tmp.path().join("dispatch.zip");
        let arch_str = arch.to_str().unwrap();

        // 1. mkdir inside archive
        let v_dir = format!("{}!/nested/sub", arch_str);
        dispatch_mkdir(&v_dir).await.unwrap();

        // 2. copy real file into archive
        let f = tmp.path().join("real.txt");
        tokio::fs::write(&f, b"hello").await.unwrap();
        let v_dest = format!("{}!/nested", arch_str);
        let count = dispatch_batch(&[f.to_str().unwrap().to_string()], &v_dest, false).await.unwrap();
        assert_eq!(count, 1);

        // 3. extract from archive to real dir
        let out_dir = tmp.path().join("output");
        let v_src = format!("{}!/nested/real.txt", arch_str);
        let ext_count = dispatch_batch(&[v_src], out_dir.to_str().unwrap(), false).await.unwrap();
        assert_eq!(ext_count, 1);
        assert!(out_dir.join("real.txt").exists());

        // 4. delete from archive
        let del_target = format!("{}!/nested/real.txt", arch_str);
        let del_count = dispatch_delete(&[del_target]).await.unwrap();
        assert_eq!(del_count, 1);
    }
}

