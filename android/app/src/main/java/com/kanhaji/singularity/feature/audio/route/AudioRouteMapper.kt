package com.kanhaji.singularity.feature.audio.route

import android.media.MediaRoute2Info
import android.os.Build
import com.kanhaji.singularity.feature.audio.model.LaptopAudioSink

object AudioRouteMapper {

    fun formatDisplayName(sink: LaptopAudioSink): String {
        val name = when {
            sink.nick.isNotBlank() -> sink.nick.trim()
            sink.description.isNotBlank() -> sink.description.trim()
            else -> sink.name.trim()
        }
        return if (name.startsWith("Laptop", ignoreCase = true)) name else "Laptop: $name"
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

    fun resolveRouteType(sink: LaptopAudioSink): Int {
        return when (sink.deviceType.lowercase()) {
            "hdmi", "monitor", "displayport" -> MediaRoute2Info.TYPE_HDMI
            "headphones", "headset" -> MediaRoute2Info.TYPE_REMOTE_SPEAKER
            else -> MediaRoute2Info.TYPE_REMOTE_SPEAKER
        }
    }

    fun buildMediaRoute(sink: LaptopAudioSink): MediaRoute2Info {
        val builder = MediaRoute2Info.Builder(sink.name, formatDisplayName(sink))
            .addFeature(MediaRoute2Info.FEATURE_LIVE_AUDIO)
            .addFeature(MediaRoute2Info.FEATURE_REMOTE_AUDIO_PLAYBACK)
            .addFeature(MediaRoute2Info.FEATURE_REMOTE_PLAYBACK)
            .setDescription(sink.description.ifBlank { "Singularity Laptop Output" })
            .setVolumeHandling(MediaRoute2Info.PLAYBACK_VOLUME_VARIABLE)
            .setVolume(sink.volume.coerceIn(0, 100))
            .setVolumeMax(100)
            .setConnectionState(MediaRoute2Info.CONNECTION_STATE_CONNECTED)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            builder.setType(resolveRouteType(sink))
        }

        return builder.build()
    }
}
