package com.kanhaji.singularity.feature.audio.model

import org.json.JSONObject

data class ActiveAudioRouteInfo(
    val outputDeviceName: String,
    val outputDeviceLabel: String,
    val sourceApp: String?,
    val sourceOrigin: String,
    val isPlaying: Boolean
) {
    companion object {
        fun fromJson(json: JSONObject): ActiveAudioRouteInfo {
            return ActiveAudioRouteInfo(
                outputDeviceName = json.optString("output_device_name", ""),
                outputDeviceLabel = json.optString("output_device_label", "Audio Output"),
                sourceApp = if (json.has("source_app") && !json.isNull("source_app")) json.optString("source_app") else null,
                sourceOrigin = json.optString("source_origin", "laptop"),
                isPlaying = json.optBoolean("is_playing", false)
            )
        }
    }

    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("output_device_name", outputDeviceName)
            put("output_device_label", outputDeviceLabel)
            if (sourceApp != null) put("source_app", sourceApp)
            put("source_origin", sourceOrigin)
            put("is_playing", isPlaying)
        }
    }
}

