package com.kanhaji.singularity.feature.files.util

import com.kanhaji.core.navigation.navigator.AppNavigator
import com.kanhaji.singularity.feature.files.FolderNavScreen
import com.kanhaji.singularity.feature.files.data.FsSelectionManager
import com.kanhaji.singularity.feature.files.security.FsSudoSessionManager

fun navigateToBreadcrumb(
    navigator: AppNavigator,
    target: String,
    onRequireAuth: ((onSuccess: () -> Unit) -> Unit)? = null
) {
    if (FsSudoSessionManager.canAccessPath(target)) {
        performBreadcrumbPopOrPush(navigator, target)
    } else if (onRequireAuth != null) {
        onRequireAuth {
            performBreadcrumbPopOrPush(navigator, target)
        }
    } else {
        performBreadcrumbPopOrPush(navigator, target)
    }
}

private fun performBreadcrumbPopOrPush(navigator: AppNavigator, target: String) {
    FsSelectionManager.clear()
    val stack = navigator.backStack
    val idx = stack.indexOfLast { (it as? FolderNavScreen)?.path == target }
    if (idx != -1) {
        while (stack.size > idx + 1) navigator.pop()
    } else {
        navigator.navigate(FolderNavScreen(target))
    }
}

private val ARCHIVE_EXTENSIONS = setOf(
    "zip", "7z", "tar", "gz", "xz", "bz2", "rar", "iso", "zst", "tgz", "txz"
)

fun isArchive(nameOrPath: String): Boolean {
    val lower = nameOrPath.lowercase()
    if (lower.endsWith(".tar.gz") || lower.endsWith(".tar.xz") || lower.endsWith(".tar.bz2")) return true
    val ext = lower.substringAfterLast('.', "")
    return ext in ARCHIVE_EXTENSIONS
}

fun isArchivePath(path: String): Boolean = path.contains("!/") || path.endsWith("!")

fun splitArchivePath(virtualPath: String): Pair<String, String> {
    val sep = if (virtualPath.contains("!/")) "!/" else "!"
    val parts = virtualPath.split(sep, limit = 2)
    val archive = parts[0]
    val internal = if (parts.size > 1) parts[1].trim('/') else ""
    return Pair(archive, internal)
}

fun formatFolderTitle(currentPath: String): String {
    if (isArchivePath(currentPath)) {
        val (archive, internal) = splitArchivePath(currentPath)
        return if (internal.isEmpty()) {
            archive.trimEnd('/').substringAfterLast('/')
        } else {
            internal.trimEnd('/').substringAfterLast('/')
        }
    }
    return when {
        currentPath == "~" || currentPath.isEmpty() -> "Home"
        currentPath == "/" -> "Root"
        currentPath.contains("/") -> currentPath.trimEnd('/').substringAfterLast('/')
        else -> currentPath
    }
}

fun buildRawFileUrl(baseUrl: String, remotePath: String): String {
    val cleanBase = baseUrl.trimEnd('/')
    return if (isArchivePath(remotePath)) {
        val (archive, internal) = splitArchivePath(remotePath)
        val encArch = java.net.URLEncoder.encode(archive, "UTF-8")
        val encEntry = java.net.URLEncoder.encode(internal, "UTF-8")
        "$cleanBase/api/fs/archive/raw?path=$encArch&entry=$encEntry"
    } else {
        val encPath = java.net.URLEncoder.encode(remotePath, "UTF-8")
        "$cleanBase/api/fs/raw?path=$encPath"
    }
}

fun buildChildPath(parent: String, childName: String): String {
    val trimmedChild = childName.trim().trimStart('/')
    return when {
        parent == "/" || parent.isEmpty() -> "/$trimmedChild"
        parent.endsWith("!") -> "$parent/$trimmedChild"
        parent.endsWith("/") -> "$parent$trimmedChild"
        else -> "$parent/$trimmedChild"
    }
}

fun buildThumbnailUrl(baseUrl: String, remotePath: String): String? {
    if (isArchivePath(remotePath)) return null
    val cleanBase = baseUrl.trimEnd('/')
    val encPath = java.net.URLEncoder.encode(remotePath, "UTF-8")
    return "$cleanBase/api/fs/thumbnail?path=$encPath"
}


