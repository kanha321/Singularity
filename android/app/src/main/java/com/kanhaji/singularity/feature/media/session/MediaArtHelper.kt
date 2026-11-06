package com.kanhaji.singularity.feature.media.session

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import java.util.Base64

object MediaArtHelper {
    private var lastKey: String? = null
    private var lastBase64: String? = null

    fun extractArt(
        uri: String?,
        bitmap: Bitmap?,
        title: String,
        artist: String
    ): Pair<String?, String?> = extractArt(uri, bitmap != null, title, artist) {
        bitmap?.let { defaultCompress(it) }
    }

    fun extractArt(
        uri: String?,
        hasBitmap: Boolean,
        title: String,
        artist: String,
        compressFn: () -> ByteArray?
    ): Pair<String?, String?> {
        if (uri?.startsWith("http://") == true || uri?.startsWith("https://") == true) {
            return Pair(uri, null)
        }

        if (!hasBitmap) {
            return Pair(uri, null)
        }

        val currentKey = "$title|$artist"
        if (currentKey == lastKey && lastBase64 != null) {
            return Pair(uri, lastBase64)
        }

        val bytes = compressFn()
        val b64 = bytes?.let { Base64.getEncoder().encodeToString(it) }
        if (b64 != null) {
            lastKey = currentKey
            lastBase64 = b64
        }
        return Pair(uri, b64)
    }

    fun defaultCompress(bitmap: Bitmap, maxDim: Int = 300): ByteArray? {
        val scaled = if (bitmap.width > maxDim || bitmap.height > maxDim) {
            val ratio = bitmap.width.toFloat() / bitmap.height.toFloat().coerceAtLeast(1f)
            val (w, h) = if (ratio > 1f) maxDim to (maxDim / ratio).toInt() else (maxDim * ratio).toInt() to maxDim
            Bitmap.createScaledBitmap(bitmap, w.coerceAtLeast(1), h.coerceAtLeast(1), true)
        } else {
            bitmap
        }
        val baos = ByteArrayOutputStream()
        return if (scaled.compress(Bitmap.CompressFormat.JPEG, 80, baos)) {
            baos.toByteArray()
        } else {
            null
        }
    }

    fun clearCache() {
        lastKey = null
        lastBase64 = null
    }
}
