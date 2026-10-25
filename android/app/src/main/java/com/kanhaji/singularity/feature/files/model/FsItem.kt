package com.kanhaji.singularity.feature.files.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable

@Serializable
data class FsItem(
    val name: String,
    val path: String,
    val is_dir: Boolean = false,
    val size: Long = 0L,
    val modified: Long = 0L,
    val mime_type: String = "",
    val is_symlink: Boolean = false,
    val is_hidden: Boolean = false
) {
    val isVideo: Boolean get() = mime_type.startsWith("video/")
    val isImage: Boolean get() = mime_type.startsWith("image/")
    val isAudio: Boolean get() = mime_type.startsWith("audio/")
    val isArchive: Boolean get() = mime_type.contains("zip") || mime_type.contains("tar") || mime_type.contains("compressed")
    val isThumbnailEligible: Boolean get() = (isImage || isVideo) && !is_dir

    val icon: ImageVector
        get() = when {
            is_dir -> Icons.Outlined.Folder
            isVideo -> Icons.Outlined.VideoFile
            isImage -> Icons.Outlined.Image
            isAudio -> Icons.Outlined.AudioFile
            isArchive -> Icons.Outlined.FolderZip
            mime_type == "application/pdf" -> Icons.Outlined.PictureAsPdf
            mime_type.startsWith("text/") -> Icons.Outlined.Description
            else -> Icons.AutoMirrored.Outlined.InsertDriveFile
        }

    val formattedSize: String
        get() {
            if (is_dir) return "Folder"
            val bytes = size
            if (bytes < 1024) return "$bytes B"
            val kb = bytes / 1024.0
            if (kb < 1024) return "%.1f KB".format(kb)
            val mb = kb / 1024.0
            if (mb < 1024) return "%.1f MB".format(mb)
            val gb = mb / 1024.0
            return "%.2f GB".format(gb)
        }

    val formattedModified: String
        get() {
            if (modified <= 0) return ""
            val sdf = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
            return sdf.format(java.util.Date(modified * 1000L))
        }
}
