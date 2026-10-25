package com.kanhaji.singularity.feature.player.util

import com.kanhaji.singularity.feature.files.util.buildRawFileUrl

object VideoFileUtils {

    private val VIDEO_EXTENSIONS = setOf(
        "mp4", "mkv", "webm", "avi", "mov",
        "flv", "m4v", "ts", "3gp", "wmv", "mpg", "mpeg"
    )

    fun isVideoFile(fileName: String): Boolean {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return extension in VIDEO_EXTENSIONS
    }

    fun buildStreamingUrl(baseUrl: String, remotePath: String): String {
        return buildRawFileUrl(baseUrl, remotePath)
    }
}
