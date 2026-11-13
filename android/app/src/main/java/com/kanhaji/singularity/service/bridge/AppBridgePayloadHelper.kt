package com.kanhaji.singularity.service.bridge

import com.kanhaji.singularity.feature.audio.model.ActiveAudioRouteInfo
import com.kanhaji.singularity.feature.audio.model.LaptopAudioSink
import com.kanhaji.singularity.feature.auth.data.AuthSettingsManager
import com.kanhaji.singularity.feature.media.model.MediaAction
import com.kanhaji.singularity.feature.media.model.MediaState
import com.kanhaji.singularity.feature.notification.PhoneNotificationTracker
import org.json.JSONArray
import org.json.JSONObject

object AppBridgePayloadHelper {

    fun parsePhoneMediaAction(json: JSONObject): MediaAction? {
        val actionObj = json.optJSONObject("phone_media_action") ?: return null
        val cmd = actionObj.optString("command", "").trim()
        if (cmd.isEmpty()) return null
        val player = actionObj.optString("player", "")
        val pos = if (actionObj.has("position_ms")) actionObj.optLong("position_ms") else null
        return MediaAction(player, cmd, pos)
    }

    fun parseAudioSinks(json: JSONObject): List<LaptopAudioSink>? {
        val arr = json.optJSONArray("audio_sinks") ?: return null
        val list = mutableListOf<LaptopAudioSink>()
        for (i in 0 until arr.length()) {
            arr.optJSONObject(i)?.let { list.add(LaptopAudioSink.fromJson(it)) }
        }
        return list
    }

    fun parseActiveAudioRoute(json: JSONObject): ActiveAudioRouteInfo? {
        val obj = json.optJSONObject("active_audio_route") ?: return null
        return ActiveAudioRouteInfo.fromJson(obj)
    }

    fun buildLockResponse(
        locked: Boolean,
        loggedIn: Boolean,
        phoneMedia: MediaState? = AppBridgeState.currentPhoneMediaState.value,
        authSettingsJson: JSONObject? = try { AuthSettingsManager.settings.value.toJson() } catch (_: Exception) { null },
        deviceName: String? = null
    ): JSONObject {
        val resp = JSONObject()
            .put("status", "ok")
            .put("pc_locked", locked)
            .put("logged_in", loggedIn)
            .put("isLoggedIn", loggedIn)
            .put("request_unlock", AppBridgeState.pendingUnlockRequest.getAndSet(false) || AppBridgeState.pendingLoginRequest.getAndSet(false))
            .put("request_lock", AppBridgeState.pendingLockRequest.getAndSet(false))
            .put("request_logout", AppBridgeState.pendingLogoutRequest.getAndSet(false))
        if (deviceName != null) resp.put("device_name", deviceName)
        if (authSettingsJson != null) resp.put("auth_settings", authSettingsJson)
        AppBridgeState.pendingPowerAction.getAndSet(null)?.let { resp.put("request_power", it) }
        AppBridgeState.pendingOpenUrl.getAndSet(null)?.let { resp.put("request_open_url", it) }
        AppBridgeState.pendingCameraAction.getAndSet(null)?.let { resp.put("request_camera", it) }
        AppBridgeState.pendingCameraLens.getAndSet(null)?.let { resp.put("camera_lens", it) }
        AppBridgeState.pendingMediaAction.getAndSet(null)?.let {
            resp.put("media_action", JSONObject().put("player", it.player).put("command", it.command).apply {
                if (it.positionMs != null) put("position_ms", it.positionMs)
            })
        }
        if (phoneMedia != null) {
            resp.put("phone_media", phoneMedia.toJson())
        }
        AppBridgeState.pendingAudioRouteCommand.getAndSet(null)?.let { resp.put("audio_route", it) }

        val (pendingNotifs, pendingDismissals) = PhoneNotificationTracker.drainPending()
        if (pendingNotifs.isNotEmpty()) {
            val arr = JSONArray()
            pendingNotifs.forEach { arr.put(it.toJson()) }
            resp.put("phone_notifications", arr)
        }
        if (pendingDismissals.isNotEmpty()) {
            val arr = JSONArray()
            pendingDismissals.forEach { arr.put(it.toJson()) }
            resp.put("phone_dismissals", arr)
        }
        resp.put("phone_ringing", com.kanhaji.singularity.feature.find.DeviceRinger.isRinging.value)
        return resp
    }
}

