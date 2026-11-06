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
    val canSeek: Boolean
) {
    val isPlaying: Boolean get() = status.equals("Playing", ignoreCase = true)

    companion object {
        fun fromJson(json: JSONObject): MediaState {
            val rawArt = json.optString("art_url").takeIf { !it.isNullOrEmpty() && it != "null" }
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
                canSeek = json.optBoolean("can_seek", false)
            )
        }
    }
}

data class MediaAction(
    val player: String,
    val command: String,
    val positionMs: Long? = null
)

