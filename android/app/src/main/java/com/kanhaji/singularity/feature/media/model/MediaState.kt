package com.kanhaji.singularity.feature.media.model

import org.json.JSONObject

data class MediaState(
    val player: String,
    val title: String,
    val artist: String,
    val album: String,
    val artUrl: String?,
    val status: String,
    val positionMs: Long,
    val durationMs: Long,
    val canPlay: Boolean,
    val canPause: Boolean,
    val canNext: Boolean,
    val canPrev: Boolean,
    val canSeek: Boolean,
    val artBase64: String? = null
) {
    val isPlaying: Boolean get() = status.equals("Playing", ignoreCase = true)

    fun toJson(): JSONObject = JSONObject().apply {
        put("player", player)
        put("title", title)
        put("artist", artist)
        put("album", album)
        if (artUrl != null) put("art_url", artUrl)
        if (artBase64 != null) put("art_base64", artBase64)
        put("status", status)
        put("position_ms", positionMs)
        put("duration_ms", durationMs)
        put("can_play", canPlay)
        put("can_pause", canPause)
        put("can_next", canNext)
        put("can_prev", canPrev)
        put("can_seek", canSeek)
    }

    companion object {
        fun fromJson(json: JSONObject): MediaState {
            val rawArt = json.optString("art_url").takeIf { !it.isNullOrEmpty() && it != "null" }
            val rawB64 = json.optString("art_base64").takeIf { !it.isNullOrEmpty() && it != "null" }
            return MediaState(
                player = json.optString("player", ""),
                title = json.optString("title", ""),
                artist = json.optString("artist", ""),
                album = json.optString("album", ""),
                artUrl = rawArt,
                status = json.optString("status", "Stopped"),
                positionMs = json.optLong("position_ms", 0L),
                durationMs = json.optLong("duration_ms", 0L),
                canPlay = json.optBoolean("can_play", true),
                canPause = json.optBoolean("can_pause", true),
                canNext = json.optBoolean("can_next", false),
                canPrev = json.optBoolean("can_prev", false),
                canSeek = json.optBoolean("can_seek", false),
                artBase64 = rawB64
            )
        }
    }
}

data class MediaAction(
    val player: String,
    val command: String,
    val positionMs: Long? = null
)
