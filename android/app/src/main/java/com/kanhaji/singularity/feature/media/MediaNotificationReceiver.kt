package com.kanhaji.singularity.feature.media

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class MediaNotificationReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_PLAY_PAUSE = "com.kanhaji.singularity.media.ACTION_PLAY_PAUSE"
        const val ACTION_PLAY = "com.kanhaji.singularity.media.ACTION_PLAY"
        const val ACTION_PAUSE = "com.kanhaji.singularity.media.ACTION_PAUSE"
        const val ACTION_PREVIOUS = "com.kanhaji.singularity.media.ACTION_PREVIOUS"
        const val ACTION_NEXT = "com.kanhaji.singularity.media.ACTION_NEXT"
        const val EXTRA_PLAYER = "extra_player"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val player = intent.getStringExtra(EXTRA_PLAYER) ?: ""
        val cmd = when (intent.action) {
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
