package com.kanhaji.singularity.feature.docs.data

import android.content.Context
import com.kanhaji.singularity.feature.files.util.buildRawFileUrl
import com.kanhaji.singularity.service.AppBridgeService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object DocumentDownloader {

    private fun getCandidateBaseUrls(): List<String> {
        val dynamicUrl = AppBridgeService.fileServerUrl.value
        return listOfNotNull(
            dynamicUrl,
            "http://127.0.0.1:8920",
            "http://localhost:8920",
            "http://10.145.206.86:8920"
        ).distinct()
    }

    suspend fun downloadToCache(
        context: Context,
        remotePath: String,
        filename: String
    ): Result<File> = withContext(Dispatchers.IO) {
        val cacheDir = File(context.cacheDir, "preview_docs").apply { mkdirs() }
        val targetFile = File(cacheDir, filename)

        val candidateUrls = getCandidateBaseUrls()
        var lastException: Exception? = null

        for (baseUrl in candidateUrls) {
            try {
                val url = URL(buildRawFileUrl(baseUrl, remotePath))
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 4000
                    readTimeout = 15000
                    requestMethod = "GET"
                }

                if (conn.responseCode in 200..299) {
                    conn.inputStream.use { input ->
                        FileOutputStream(targetFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    return@withContext Result.success(targetFile)
                }
            } catch (e: Exception) {
                lastException = e
            }
        }

        Result.failure(lastException ?: IllegalStateException("Failed to download document"))
    }
}
