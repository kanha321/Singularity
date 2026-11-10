package com.kanhaji.singularity.service.bridge

import android.content.Context
import android.util.Log
import com.kanhaji.singularity.biometric.BiometricAuthActivity
import com.kanhaji.singularity.feature.auth.data.AuthSettingsManager
import com.kanhaji.singularity.feature.media.MediaControlManager
import com.kanhaji.singularity.feature.media.model.MediaState
import com.kanhaji.singularity.feature.media.session.SingularityMediaListenerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.PrintWriter

object AppBridgeStreamHandler {
    private const val TAG = "SingularityStreamHandler"

    suspend fun handleStream(
        context: Context,
        inputStream: InputStream,
        outputStream: OutputStream,
        onTerminate: () -> Unit,
        onClose: () -> Unit
    ) = withContext(Dispatchers.IO) {
        AppBridgeState.activeConnections.incrementAndGet()
        try {
            val reader = BufferedReader(InputStreamReader(inputStream))
            val writer = PrintWriter(outputStream, true)
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val reqStr = line?.trim() ?: continue
                if (reqStr.isEmpty()) continue
                AppBridgeState.lastPacketReceivedMs.set(System.currentTimeMillis())
                if (!AppBridgeState.isPcConnected.value) {
                    AppBridgeState.isPcConnected.value = true
                    AppBridgeNotificationHelper.updateHighwayNotification(context, true)
                }
                val json = try { JSONObject(reqStr) } catch (_: Exception) {
                    writer.println(JSONObject().put("status", "error").put("message", "invalid json").toString())
                    continue
                }
                val resp = dispatchAction(context, json, writer, onTerminate)
                if (resp != null) writer.println(resp.toString())
            }
        } catch (e: Exception) {
            Log.d(TAG, "Stream ended: ${e.message}")
        } finally {
            AppBridgeState.activeConnections.decrementAndGet()
            onClose()
        }
    }

    private suspend fun dispatchAction(context: Context, json: JSONObject, writer: PrintWriter, onTerminate: () -> Unit): JSONObject? {
        return when (json.optString("action")) {
            "ping" -> JSONObject().put("status", "pong")
            "status" -> JSONObject().put("status", "online").put("package", context.packageName)
            "shutdown", "terminate_service" -> {
                writer.println(JSONObject().put("status", "shutting_down").toString())
                writer.flush()
                onTerminate()
                null
            }
            "authenticate" -> {
                val reason = json.optString("reason", "Host Authentication")
                val timeout = json.optLong("timeout_secs", 15L)
                val authAction = json.optString("auth_action", json.optString("action_type", "sudo"))
                BiometricAuthBridge.processBiometricAuth(context, authAction, reason, timeout) { event, attempt, message ->
                    try {
                        val pJson = JSONObject().put("event", event).put("attempt", attempt)
                        if (message != null) pJson.put("message", message)
                        writer.println(pJson.toString())
                        writer.flush()
                    } catch (_: Exception) {}
                }
            }
            "cancel_auth" -> {
                BiometricAuthBridge.rejectActiveAuth("Cancelled by user on host")
                BiometricAuthActivity.dismissActivePrompt()
                AppBridgeNotificationHelper.cancelAuthNotification(context)
                JSONObject().put("status", "cancelled")
            }
            "lock_state" -> handleLockState(context, json)
            else -> JSONObject().put("status", "unknown_action")
        }
    }

    private fun handleLockState(context: Context, json: JSONObject): JSONObject {
        AppBridgeState.updateLockState(json)
        try { MediaControlManager.getInstance(context).updateMedia(AppBridgeState.currentMediaState.value) } catch (_: Exception) {}
        AppBridgePayloadHelper.parsePhoneMediaAction(json)?.let {
            SingularityMediaListenerService.tracker.executeAction(it)
        }
        AppBridgePayloadHelper.parseAudioSinks(json)?.let { sinks ->
            AppBridgeState.availableLaptopAudioSinks.value = sinks
            com.kanhaji.singularity.feature.audio.route.AudioRoutePreferences.restoreIfAvailable(sinks)
        }
        AppBridgeState.activeAudioRoute.value = AppBridgePayloadHelper.parseActiveAudioRoute(json)
        val locked = json.optBoolean("locked", false)
        val loggedIn = json.optBoolean("logged_in", json.optBoolean("isLoggedIn", false))
        LockTransitionHelper.handleLockTransition(context, locked, loggedIn)
        return AppBridgePayloadHelper.buildLockResponse(locked, loggedIn)
    }
}
