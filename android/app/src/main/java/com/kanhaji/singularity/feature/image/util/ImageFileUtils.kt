package com.kanhaji.singularity.feature.image.util

import com.kanhaji.singularity.feature.files.util.buildRawFileUrl
import com.kanhaji.singularity.service.AppBridgeService

object ImageFileUtils {

    private val IMAGE_EXTENSIONS = setOf(
        "jpg", "jpeg", "png", "webp", "gif",
        "bmp", "svg", "ico", "heic", "heif",
        "tiff", "tif"
    )

    fun isImageFile(filename: String): Boolean {
        val ext = filename.substringAfterLast('.', "").lowercase()
        return ext in IMAGE_EXTENSIONS
    }

    fun buildImageUrl(remotePath: String): String {
        val baseUrl = AppBridgeService.fileServerUrl.value ?: "http://127.0.0.1:8920"
        return buildRawFileUrl(baseUrl, remotePath)
    }
}

