package com.kanhaji.singularity.feature.docs.data

import com.kanhaji.singularity.feature.files.util.buildRawFileUrl
import com.kanhaji.singularity.service.AppBridgeService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

object TextContentRepository {

    private const val MAX_BYTES = 5 * 1024 * 1024L // 5 MB preview ceiling

    private fun getCandidateBaseUrls(): List<String> {
        val dynamicUrl = AppBridgeService.fileServerUrl.value
        return listOfNotNull(
            dynamicUrl,
            "http://127.0.0.1:8920",
            "http://localhost:8920",
            "http://10.145.206.86:8920"
        ).distinct()
    }

    suspend fun fetchText(remotePath: String): Result<String> = withContext(Dispatchers.IO) {
        val candidateUrls = getCandidateBaseUrls()
        var lastException: Exception? = null

        for (baseUrl in candidateUrls) {
            try {
                val url = URL(buildRawFileUrl(baseUrl, remotePath))
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 3000
                    readTimeout = 8000
                    requestMethod = "GET"
                }

                if (conn.responseCode in 200..299) {
                    val contentLength = conn.contentLengthLong
                    val isTruncated = contentLength > MAX_BYTES

                    val text = conn.inputStream.use { stream ->
                        readStreamWithLimit(stream, MAX_BYTES)
                    }

                    val resultText = if (isTruncated) {
                        text + "\n\n--- [Preview truncated: file size exceeds 5MB] ---"
                    } else {
                        text
                    }
                    return@withContext Result.success(resultText)
                }
            } catch (e: Exception) {
                lastException = e
            }
        }

        Result.failure(lastException ?: IllegalStateException("Failed to load text file"))
    }

    private fun readStreamWithLimit(stream: InputStream, limit: Long): String {
        val buffer = ByteArray(8192)
        val out = java.io.ByteArrayOutputStream()
        var totalRead = 0L

        while (true) {
            val toRead = minOf(buffer.size.toLong(), limit - totalRead).toInt()
            if (toRead <= 0) break
            val read = stream.read(buffer, 0, toRead)
            if (read == -1) break
            out.write(buffer, 0, read)
            totalRead += read
        }
        return out.toString("UTF-8")
    }
}
