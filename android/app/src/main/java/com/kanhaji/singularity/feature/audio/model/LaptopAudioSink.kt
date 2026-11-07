package com.kanhaji.singularity.feature.audio.model

import org.json.JSONObject

data class LaptopAudioSink(
    val name: String,
    val description: String,
    val nick: String,
    val deviceType: String,
    val isDefault: Boolean,
    val volume: Int,
    val mute: Boolean
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name)
        put("description", description)
        put("nick", nick)
        put("device_type", deviceType)
        put("is_default", isDefault)
        put("volume", volume)
        put("mute", mute)
    }

    companion object {
        fun fromJson(json: JSONObject): LaptopAudioSink = LaptopAudioSink(
            name = json.optString("name", ""),
            description = json.optString("description", ""),
            nick = json.optString("nick", json.optString("description", "")),
            deviceType = json.optString("device_type", "generic"),
            isDefault = json.optBoolean("is_default", false),
            volume = json.optInt("volume", 100).coerceIn(0, 150),
            mute = json.optBoolean("mute", false)
        )
    }
}

