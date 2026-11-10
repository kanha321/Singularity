package com.kanhaji.singularity.feature.audio.recovery

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket

object CompanionClient {
    private const val TAG = "CompanionClient"
    private const val PORT = 2830
    private const val TIMEOUT_MS = 600

    data class DisconnectResult(
        val ok: Boolean,
        val killedPids: List<Int>,
        val serviceStopped: Boolean
    )

    suspend fun disconnectCleanup(appForeground: Boolean): DisconnectResult = withContext(Dispatchers.IO) {
        val req = JSONObject().apply {
            put("action", "disconnect_cleanup")
            put("app_foreground", appForeground)
        }.toString()

        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", PORT), TIMEOUT_MS)
                socket.soTimeout = TIMEOUT_MS
                val writer = PrintWriter(socket.getOutputStream(), true)
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                writer.println(req)
                val resp = reader.readLine()
                if (resp != null) {
                    val json = JSONObject(resp)
                    val pidsArray = json.optJSONArray("killed_pids")
                    val pids = mutableListOf<Int>()
                    if (pidsArray != null) {
                        for (i in 0 until pidsArray.length()) {
                            pids.add(pidsArray.getInt(i))
                        }
                    }
                    val stopped = json.optBoolean("service_stopped", false)
                    Log.i(TAG, "Companion cleanup success: killed=$pids, serviceStopped=$stopped")
                    return@withContext DisconnectResult(ok = true, killedPids = pids, serviceStopped = stopped)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Companion disconnectCleanup failed (is companion running?): ${e.message}")
        }
        DisconnectResult(ok = false, killedPids = emptyList(), serviceStopped = false)
    }

    suspend fun ping(): Boolean = withContext(Dispatchers.IO) {
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", PORT), TIMEOUT_MS)
                socket.soTimeout = TIMEOUT_MS
                val writer = PrintWriter(socket.getOutputStream(), true)
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                writer.println("{\"action\":\"ping\"}")
                val resp = reader.readLine()
                return@withContext resp?.contains("\"ok\":true") == true
            }
        } catch (_: Exception) {
            false
        }
    }

    fun triggerDisconnect(context: android.content.Context?) {
        val isFg = com.kanhaji.singularity.service.bridge.AppLifecycleTracker.isForeground
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            disconnectCleanup(isFg)
        }
    }
}
