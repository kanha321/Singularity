package com.kanhaji.singularity.feature.files.download

import com.kanhaji.singularity.feature.files.model.FsItem
import kotlinx.serialization.Serializable

@Serializable
data class DownloadTarget(
    val remotePath: String,
    val relativeLocalPath: String,
    val size: Long
)

data class ResolvedDownload(
    val rootLabel: String,
    val directories: List<String>,
    val targets: List<DownloadTarget>,
    val totalBytes: Long
)

object FolderDownloadResolver {

    val DEFAULT_EXCLUDED_DIRS = setOf(
        ".git", "node_modules", "target", ".gradle", ".idea", ".vscode", ".cache", "__pycache__", "build"
    )
    val DEFAULT_EXCLUDED_FILES = setOf(
        ".DS_Store", "Thumbs.db"
    )

    fun isExcludedDir(name: String): Boolean = name in DEFAULT_EXCLUDED_DIRS

    fun isExcludedFile(name: String): Boolean {
        if (name in DEFAULT_EXCLUDED_FILES) return true
        if (name.endsWith(".tmp", ignoreCase = true) || name.endsWith(".swp", ignoreCase = true)) return true
        return false
    }

    suspend fun resolve(
        items: List<FsItem>,
        fetchDirectory: suspend (String) -> Result<List<FsItem>>
    ): ResolvedDownload {
        val targets = mutableListOf<DownloadTarget>()
        val directories = mutableListOf<String>()

        for (item in items) {
            if (item.is_dir) {
                directories.add(item.name)
                resolveDirectory(
                    dirPath = item.path,
                    parentRelativePath = item.name,
                    fetchDirectory = fetchDirectory,
                    outDirs = directories,
                    outTargets = targets
                )
            } else if (!isExcludedFile(item.name)) {
                targets.add(DownloadTarget(item.path, item.name, item.size))
            }
        }

        val rootLabel = when {
            items.isEmpty() -> ""
            items.size == 1 -> items[0].name
            else -> "${items[0].name} + ${items.size - 1} items"
        }

        return ResolvedDownload(
            rootLabel = rootLabel,
            directories = directories,
            targets = targets,
            totalBytes = targets.sumOf { it.size }
        )
    }

    private suspend fun resolveDirectory(
        dirPath: String,
        parentRelativePath: String,
        fetchDirectory: suspend (String) -> Result<List<FsItem>>,
        outDirs: MutableList<String>,
        outTargets: MutableList<DownloadTarget>
    ) {
        val result = fetchDirectory(dirPath)
        val children = result.getOrNull() ?: return

        for (child in children) {
            val childRelative = "$parentRelativePath/${child.name}"
            if (child.is_dir) {
                if (!isExcludedDir(child.name)) {
                    outDirs.add(childRelative)
                    resolveDirectory(child.path, childRelative, fetchDirectory, outDirs, outTargets)
                }
            } else if (!isExcludedFile(child.name)) {
                outTargets.add(DownloadTarget(child.path, childRelative, child.size))
            }
        }
    }
}
