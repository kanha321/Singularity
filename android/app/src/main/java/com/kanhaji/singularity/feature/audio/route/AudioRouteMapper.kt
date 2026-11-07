package com.kanhaji.singularity.feature.audio.route

import android.media.MediaRoute2Info
import com.kanhaji.singularity.feature.audio.model.LaptopAudioSink

object AudioRouteMapper {

    fun formatDisplayName(sink: LaptopAudioSink): String {
        val name = when {
            sink.nick.isNotBlank() -> sink.nick.trim()
            sink.description.isNotBlank() -> sink.description.trim()
            else -> sink.name.trim()
        }
        return if (name.startsWith("Laptop", ignoreCase = true)) {
            name
        } else {
            "Laptop: $name"
        }
    }

    fun resolveIconType(sink: LaptopAudioSink): String {
        return when (sink.deviceType.lowercase()) {
            "hdmi", "monitor", "displayport" -> "display"
            "headphones", "headset" -> "headphones"
            "speaker" -> "speaker"
            "bluetooth" -> "bluetooth"
            else -> "laptop"
        }
    }

    fun buildMediaRoute(sink: LaptopAudioSink): MediaRoute2Info {
        return MediaRoute2Info.Builder(sink.name, formatDisplayName(sink))
            .addFeature(MediaRoute2Info.FEATURE_REMOTE_PLAYBACK)
            .addFeature(MediaRoute2Info.FEATURE_REMOTE_AUDIO_PLAYBACK)
            .setDescription(sink.description.ifBlank { "Singularity Laptop Output" })
            .setVolumeHandling(MediaRoute2Info.PLAYBACK_VOLUME_VARIABLE)
            .setVolume(sink.volume.coerceIn(0, 100))
            .setVolumeMax(100)
            .setConnectionState(MediaRoute2Info.CONNECTION_STATE_CONNECTED)
            .build()
    }
}

