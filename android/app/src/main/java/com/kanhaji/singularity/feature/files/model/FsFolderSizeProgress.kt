package com.kanhaji.singularity.feature.files.model

import java.util.Locale

data class FsFolderSizeProgress(
    val bytes: Long = 0L,
    val filesCount: Long = 0L,
    val foldersCount: Long = 0L,
    val isDone: Boolean = false,
    val error: String? = null
) {
    val formattedSize: String
        get() {
            if (bytes <= 0L) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            var b = bytes.toDouble()
            var idx = 0
            while (b >= 1024.0 && idx < units.lastIndex) {
                b /= 1024.0
                idx++
            }
            return String.format(Locale.US, "%.1f %s", b, units[idx])
        }
}
