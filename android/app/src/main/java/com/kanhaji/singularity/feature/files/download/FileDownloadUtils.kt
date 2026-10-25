package com.kanhaji.singularity.feature.files.download

import java.util.Locale

object FileDownloadUtils {

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
        val gb = mb / 1024.0
        return String.format(Locale.US, "%.2f GB", gb)
    }

    fun calculatePercentage(current: Long, total: Long): Int {
        if (total <= 0) return 0
        return ((current.toDouble() / total.toDouble()) * 100).toInt().coerceIn(0, 100)
    }

    fun formatRangeHeader(offset: Long): String? {
        return if (offset > 0) "bytes=$offset-" else null
    }

    fun parseTotalFromContentRange(contentRange: String?): Long? {
        if (contentRange.isNullOrBlank()) return null
        return contentRange.substringAfterLast('/', "").trim().toLongOrNull()
    }
}

