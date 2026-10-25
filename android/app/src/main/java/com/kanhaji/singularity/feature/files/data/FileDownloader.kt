package com.kanhaji.singularity.feature.files.data

import android.os.Environment
import com.kanhaji.singularity.feature.files.download.DownloadTarget
import com.kanhaji.singularity.feature.files.download.FileDownloadUtils
import com.kanhaji.singularity.feature.files.model.FsItem
import com.kanhaji.singularity.service.AppBridgeService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object FileDownloader {
    private const val DIRECTORY_NAME = "Singularity"

    fun isTargetAlreadySynced(target: DownloadTarget): Boolean {
        if (target.size <= 0) return false
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val destFile = File(File(downloadsDir, DIRECTORY_NAME), target.relativeLocalPath)
        return destFile.exists() && destFile.length() == target.size
    }

    suspend fun downloadTarget(
        target: DownloadTarget,
        onByteProgress: ((bytesRead: Long, totalBytes: Long, isResuming: Boolean) -> Unit)? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        val baseUrl = AppBridgeService.fileServerUrl.value ?: "http://127.0.0.1:8920"
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val targetDir = File(downloadsDir, DIRECTORY_NAME).apply { if (!exists()) mkdirs() }

        val destFile = File(targetDir, target.relativeLocalPath)
        destFile.parentFile?.let { if (!it.exists()) it.mkdirs() }
        val partFile = File(destFile.parentFile, "${destFile.name}.part")

        if (isTargetAlreadySynced(target)) {
            onByteProgress?.invoke(target.size, target.size, false)
            return@withContext Result.success(destFile)
        }

        val existingBytes = if (partFile.exists()) partFile.length() else 0L
        if (existingBytes > 0 && target.size > 0 && existingBytes == target.size) {
            if (destFile.exists()) destFile.delete()
            if (partFile.renameTo(destFile)) {
                onByteProgress?.invoke(target.size, target.size, true)
                return@withContext Result.success(destFile)
            }
        }

        try {
            val encoded = URLEncoder.encode(target.remotePath, "UTF-8")
            val url = URL("$baseUrl/api/fs/raw?path=$encoded")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 15000
                requestMethod = "GET"
                FileDownloadUtils.formatRangeHeader(existingBytes)?.let { header ->
                    setRequestProperty("Range", header)
                }
            }

            val code = conn.responseCode
            if (code != 200 && code != 206) {
                if (code == 416) partFile.delete()
                return@withContext Result.failure(Exception("HTTP $code downloading file"))
            }

            val isResuming = code == 206 && existingBytes > 0
            val startOffset = if (isResuming) existingBytes else 0L
            val totalSize = when {
                target.size > 0 -> target.size
                isResuming -> {
                    val cr = conn.getHeaderField("Content-Range")
                    FileDownloadUtils.parseTotalFromContentRange(cr) ?: (startOffset + conn.contentLengthLong)
                }
                else -> conn.contentLengthLong.takeIf { it > 0 } ?: 0L
            }

            conn.inputStream.use { input ->
                FileOutputStream(partFile, isResuming).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesCopied = startOffset
                    var lastReport = 0L

                    onByteProgress?.invoke(bytesCopied, totalSize, isResuming)

                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        bytesCopied += read

                        val now = System.currentTimeMillis()
                        if (now - lastReport > 200) {
                            lastReport = now
                            onByteProgress?.invoke(bytesCopied, totalSize, isResuming)
                        }
                    }
                    onByteProgress?.invoke(bytesCopied, totalSize, isResuming)
                }
            }

            if (destFile.exists()) destFile.delete()
            if (!partFile.renameTo(destFile)) {
                return@withContext Result.failure(Exception("Failed to finalize saved file"))
            }

            Result.success(destFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadFile(
        item: FsItem,
        onByteProgress: ((bytesRead: Long, totalBytes: Long) -> Unit)? = null
    ): Result<File> {
        if (item.is_dir) {
            return Result.failure(IllegalArgumentException("Use downloadTarget for directories"))
        }
        return downloadTarget(DownloadTarget(item.path, item.name, item.size)) { read, total, _ ->
            onByteProgress?.invoke(read, total)
        }
    }
}
