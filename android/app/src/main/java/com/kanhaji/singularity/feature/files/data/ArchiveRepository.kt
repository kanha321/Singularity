package com.kanhaji.singularity.feature.files.data

import com.kanhaji.singularity.feature.files.model.FsItem
import com.kanhaji.singularity.service.AppBridgeService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

data class RawArchiveEntry(
    val path: String,
    val is_dir: Boolean,
    val size: Long,
    val modified: Long = 0L
)

object ArchiveRepository {
    private val cache = ConcurrentHashMap<String, List<RawArchiveEntry>>()

    private fun parseTimestamp(dateStr: String): Long {
        if (dateStr.isBlank()) return 0L
        return try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
            sdf.parse(dateStr)?.time?.div(1000L) ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    fun clearCache() {
        cache.clear()
    }

    fun filterEntries(
        archivePath: String,
        allEntries: List<RawArchiveEntry>,
        internalDir: String
    ): List<FsItem> {
        val targetDir = internalDir.trim('/').let { if (it.isEmpty()) "" else "$it/" }
        val directFolders = mutableSetOf<String>()
        val directFiles = mutableListOf<FsItem>()

        for (entry in allEntries) {
            val p = entry.path.trim('/')
            if (!p.startsWith(targetDir) || p == targetDir.trimEnd('/')) continue

            val relative = p.removePrefix(targetDir)
            if (relative.contains('/')) {
                directFolders.add(relative.substringBefore('/'))
            } else if (entry.is_dir) {
                directFolders.add(relative)
            } else {
                directFiles.add(
                    FsItem(
                        name = relative,
                        path = "$archivePath!/$p",
                        is_dir = false,
                        size = entry.size,
                        modified = entry.modified
                    )
                )
            }
        }

        val folderItems = directFolders.map { name ->
            val fullRel = if (targetDir.isEmpty()) name else "$targetDir$name"
            FsItem(
                name = name,
                path = "$archivePath!/$fullRel",
                is_dir = true,
                size = 0,
                modified = 0L
            )
        }

        return (folderItems + directFiles).sortedWith(
            compareBy<FsItem> { !it.is_dir }.thenBy { it.name.lowercase() }
        )
    }

    suspend fun listArchive(archivePath: String, internalDir: String): Result<List<FsItem>> = withContext(Dispatchers.IO) {
        val cached = cache[archivePath]
        if (cached != null) {
            return@withContext Result.success(filterEntries(archivePath, cached, internalDir))
        }

        try {
            val baseUrl = AppBridgeService.fileServerUrl.value ?: "http://127.0.0.1:8920"
            val encodedPath = URLEncoder.encode(archivePath, "UTF-8")
            val url = URL("$baseUrl/api/fs/archive/list?path=$encodedPath")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 15000
                requestMethod = "GET"
            }

            if (conn.responseCode in 200..299) {
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                val arr = JSONArray(jsonStr)
                val entries = mutableListOf<RawArchiveEntry>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    entries.add(
                        RawArchiveEntry(
                            path = obj.getString("path"),
                            is_dir = obj.optBoolean("is_dir", false),
                            size = obj.optLong("size", 0L),
                            modified = parseTimestamp(obj.optString("modified", ""))
                        )
                    )
                }
                cache[archivePath] = entries
                Result.success(filterEntries(archivePath, entries, internalDir))
            } else {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP ${conn.responseCode}"
                Result.failure(Exception(err))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun extractArchive(archivePath: String, destDir: String): Result<Unit> {
        val json = JSONObject().put("path", archivePath).put("destination_dir", destDir)
        return FileOperationsRepository.postJson("/api/fs/archive/extract", json).map { }
    }

    suspend fun compressToArchive(sources: List<String>, archivePath: String): Result<Unit> {
        val json = JSONObject().put("sources", JSONArray(sources)).put("archive_path", archivePath)
        return FileOperationsRepository.postJson("/api/fs/archive/compress", json).map { }
    }
}
