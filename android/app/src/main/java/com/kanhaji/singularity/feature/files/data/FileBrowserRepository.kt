package com.kanhaji.singularity.feature.files.data

import android.util.Log
import com.kanhaji.singularity.feature.files.model.FsFolderSizeProgress
import com.kanhaji.singularity.feature.files.model.FsItem
import com.kanhaji.singularity.service.AppBridgeService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object FileBrowserRepository {
    private const val TAG = "FileBrowserRepo"

    private val cache = java.util.concurrent.ConcurrentHashMap<String, List<FsItem>>()

    fun getCached(path: String): List<FsItem>? = cache[path]

    fun invalidateCache(path: String? = null) {
        if (path == null) cache.clear() else cache.remove(path)
    }

    private fun getCandidateBaseUrls(): List<String> {
        val dynamicUrl = AppBridgeService.fileServerUrl.value
        return listOfNotNull(
            dynamicUrl,
            "http://127.0.0.1:8920",
            "http://localhost:8920",
            "http://10.145.206.86:8920"
        ).distinct()
    }

    suspend fun listDirectory(path: String): Result<List<FsItem>> = withContext(Dispatchers.IO) {
        val candidateUrls = getCandidateBaseUrls()
        var lastException: Exception? = null

        for (baseUrl in candidateUrls) {
            try {
                val encoded = URLEncoder.encode(path, "UTF-8")
                val url = URL("$baseUrl/api/fs/list?path=$encoded")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 2000
                    readTimeout = 4000
                    requestMethod = "GET"
                }

                if (conn.responseCode == 200) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val jsonArray = JSONArray(body)
                    val items = mutableListOf<FsItem>()

                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        items.add(
                            FsItem(
                                name = obj.optString("name"),
                                path = obj.optString("path"),
                                is_dir = obj.optBoolean("is_dir"),
                                size = obj.optLong("size"),
                                modified = obj.optLong("modified"),
                                mime_type = obj.optString("mime_type"),
                                is_symlink = obj.optBoolean("is_symlink"),
                                is_hidden = obj.optBoolean("is_hidden")
                            )
                        )
                    }

                    cache[path] = items
                    AppBridgeService.fileServerUrl.value = baseUrl
                    return@withContext Result.success(items)
                } else {
                    lastException = Exception("HTTP ${conn.responseCode} from $baseUrl")
                }
            } catch (e: Exception) {
                Log.d(TAG, "Connection to $baseUrl failed: ${e.message}, trying next...")
                lastException = e
            }
        }

        Log.e(TAG, "All candidate URLs failed for path: $path", lastException)
        Result.failure(lastException ?: Exception("Could not connect to laptop file server"))
    }

    fun streamFolderSize(path: String): Flow<FsFolderSizeProgress> = flow {
        val candidateUrls = getCandidateBaseUrls()
        var connected = false

        for (baseUrl in candidateUrls) {
            var conn: HttpURLConnection? = null
            try {
                val encoded = URLEncoder.encode(path, "UTF-8")
                val url = URL("$baseUrl/api/fs/folder_size?path=$encoded")
                conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 3000
                    readTimeout = 30000
                    requestMethod = "GET"
                }

                if (conn.responseCode == 200) {
                    conn.inputStream.bufferedReader().use { reader ->
                        while (currentCoroutineContext().isActive) {
                            val line = reader.readLine() ?: break
                            if (line.isNotBlank()) {
                                val obj = JSONObject(line)
                                emit(
                                    FsFolderSizeProgress(
                                        bytes = obj.optLong("bytes"),
                                        filesCount = obj.optLong("files"),
                                        foldersCount = obj.optLong("folders"),
                                        isDone = obj.optBoolean("done"),
                                        error = obj.optString("error").takeIf { it.isNotEmpty() }
                                    )
                                )
                                if (obj.optBoolean("done")) break
                            }
                        }
                    }
                    connected = true
                    AppBridgeService.fileServerUrl.value = baseUrl
                    break
                }
            } catch (e: Exception) {
                Log.d(TAG, "streamFolderSize on $baseUrl failed: ${e.message}")
            } finally {
                conn?.disconnect()
            }
        }

        if (!connected) {
            emit(FsFolderSizeProgress(error = "Connection failed", isDone = true))
        }
    }.flowOn(Dispatchers.IO)
}
