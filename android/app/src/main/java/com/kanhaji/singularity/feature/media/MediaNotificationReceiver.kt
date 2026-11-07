package com.kanhaji.singularity.feature.media

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kanhaji.singularity.service.bridge.AppBridgeState

class MediaNotificationReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_PLAY_PAUSE = "com.kanhaji.singularity.media.ACTION_PLAY_PAUSE"
        const val ACTION_PLAY = "com.kanhaji.singularity.media.ACTION_PLAY"
        const val ACTION_PAUSE = "com.kanhaji.singularity.media.ACTION_PAUSE"
        const val ACTION_PREVIOUS = "com.kanhaji.singularity.media.ACTION_PREVIOUS"
        const val ACTION_NEXT = "com.kanhaji.singularity.media.ACTION_NEXT"
        const val ACTION_VOLUME_UP = "com.kanhaji.singularity.media.ACTION_VOLUME_UP"
        const val ACTION_VOLUME_DOWN = "com.kanhaji.singularity.media.ACTION_VOLUME_DOWN"
        const val ACTION_TOGGLE_MUTE = "com.kanhaji.singularity.media.ACTION_TOGGLE_MUTE"
        const val EXTRA_PLAYER = "extra_player"
    }

    override fun onReceive(context: Context, intent: Intent) {
        processAction(intent.action, context, intent.getStringExtra(EXTRA_PLAYER) ?: "")
    }

    fun processAction(action: String?, context: Context? = null, player: String = "") {
        when (action) {
            ACTION_VOLUME_UP -> handleVolumeChange(step = 5)
            ACTION_VOLUME_DOWN -> handleVolumeChange(step = -5)
            ACTION_TOGGLE_MUTE -> handleToggleMute()
            else -> if (context != null && action != null) handlePlaybackAction(context, action, player)
        }
    }

    private fun handleVolumeChange(step: Int) {
        val sinks = AppBridgeState.availableLaptopAudioSinks.value
        val target = sinks.find { it.isDefault } ?: sinks.firstOrNull() ?: return
        val newVol = (target.volume + step).coerceIn(0, 100)
        AppBridgeState.setSinkVolume(target.name, newVol)
    }

    private fun handleToggleMute() {
        val sinks = AppBridgeState.availableLaptopAudioSinks.value
        val target = sinks.find { it.isDefault } ?: sinks.firstOrNull() ?: return
        AppBridgeState.setSinkMute(target.name, !target.mute)
    }

    private fun handlePlaybackAction(context: Context, action: String, player: String) {
        val cmd = when (action) {
            ACTION_PLAY_PAUSE -> "play_pause"
            ACTION_PLAY -> "play"
            ACTION_PAUSE -> "pause"
            ACTION_PREVIOUS -> "previous"
            ACTION_NEXT -> "next"
            else -> return
        }
        val mgr = MediaControlManager.getInstance(context)
        when (cmd) {
            "play" -> mgr.play(player)
            "pause" -> mgr.pause(player)
            else -> mgr.dispatchCommand(cmd, player)
        }
    }
}
