package com.kanhaji.singularity.service.bridge

import com.kanhaji.singularity.feature.auth.data.AuthSettingsManager
import com.kanhaji.singularity.feature.media.model.MediaAction
import com.kanhaji.singularity.feature.media.model.MediaState
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

    fun buildLockResponse(
        locked: Boolean,
        loggedIn: Boolean,
        phoneMedia: MediaState? = AppBridgeState.currentPhoneMediaState.value,
        authSettingsJson: JSONObject? = try { AuthSettingsManager.settings.value.toJson() } catch (_: Exception) { null }
    ): JSONObject {
        val resp = JSONObject()
            .put("status", "ok")
            .put("pc_locked", locked)
            .put("logged_in", loggedIn)
            .put("isLoggedIn", loggedIn)
            .put("request_unlock", AppBridgeState.pendingUnlockRequest.getAndSet(false) || AppBridgeState.pendingLoginRequest.getAndSet(false))
            .put("request_lock", AppBridgeState.pendingLockRequest.getAndSet(false))
            .put("request_logout", AppBridgeState.pendingLogoutRequest.getAndSet(false))
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
        return resp
    }
}

