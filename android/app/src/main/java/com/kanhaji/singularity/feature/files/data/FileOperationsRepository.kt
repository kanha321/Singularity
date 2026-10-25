package com.kanhaji.singularity.feature.files.data

import android.util.Log
import com.kanhaji.singularity.service.AppBridgeService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object FileOperationsRepository {
    private const val TAG = "FileOpsRepo"

    private fun getCandidateBaseUrls(): List<String> {
        val dynamicUrl = AppBridgeService.fileServerUrl.value
        return listOfNotNull(
            dynamicUrl,
            "http://127.0.0.1:8920",
            "http://localhost:8920",
            "http://10.145.206.86:8920"
        ).distinct()
    }

    internal suspend fun postJson(route: String, json: JSONObject): Result<JSONObject> = withContext(Dispatchers.IO) {
        val candidateUrls = getCandidateBaseUrls()
        val jsonBytes = json.toString().toByteArray(Charsets.UTF_8)
        var lastException: Exception? = null

        for (baseUrl in candidateUrls) {
            var conn: HttpURLConnection? = null
            try {
                val url = URL("$baseUrl$route")
                conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 3000
                    readTimeout = 15000
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Content-Length", jsonBytes.size.toString())
                    doOutput = true
                }
                conn.outputStream.use { it.write(jsonBytes) }

                val code = conn.responseCode
                if (code in 200..299) {
                    val resp = conn.inputStream.bufferedReader().use { it.readText() }
                    AppBridgeService.fileServerUrl.value = baseUrl
                    FileBrowserRepository.invalidateCache()
                    ArchiveRepository.clearCache()
                    return@withContext Result.success(JSONObject(resp))
                } else {
                    val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
                    lastException = Exception(err)
                }
            } catch (e: Exception) {
                Log.d(TAG, "Request to $baseUrl$route failed: ${e.message}")
                lastException = e
            } finally {
                conn?.disconnect()
            }
        }
        Result.failure(lastException ?: Exception("Network failure"))
    }

    suspend fun createDirectory(path: String): Result<Unit> {
        val json = JSONObject().put("path", path)
        return postJson("/api/fs/mkdir", json).map { }
    }

    suspend fun rename(oldPath: String, newPath: String): Result<Unit> {
        val json = JSONObject().put("old_path", oldPath).put("new_path", newPath)
        return postJson("/api/fs/rename", json).map { }
    }

    suspend fun delete(paths: List<String>): Result<Int> {
        val arr = JSONArray(paths)
        val json = JSONObject().put("paths", arr)
        return postJson("/api/fs/delete", json).map { it.optInt("count", paths.size) }
    }

    suspend fun copy(sources: List<String>, destinationDir: String): Result<Int> {
        val arr = JSONArray(sources)
        val json = JSONObject().put("sources", arr).put("destination_dir", destinationDir)
        return postJson("/api/fs/copy", json).map { it.optInt("count", sources.size) }
    }

    suspend fun move(sources: List<String>, destinationDir: String): Result<Int> {
        val arr = JSONArray(sources)
        val json = JSONObject().put("sources", arr).put("destination_dir", destinationDir)
        return postJson("/api/fs/move", json).map { it.optInt("count", sources.size) }
    }
}
