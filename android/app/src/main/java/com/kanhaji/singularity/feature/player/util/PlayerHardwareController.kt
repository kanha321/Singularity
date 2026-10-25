package com.kanhaji.singularity.feature.player.util

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import kotlin.math.roundToInt

object PlayerHardwareController {

    fun getScreenBrightness(activity: Activity): Int {
        val windowBrightness = activity.window.attributes.screenBrightness
        return if (windowBrightness >= 0f) {
            (windowBrightness * 100f).roundToInt().coerceIn(0, 100)
        } else {
            try {
                val systemBrightness = Settings.System.getInt(
                    activity.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS,
                    128
                )
                ((systemBrightness / 255f) * 100f).roundToInt().coerceIn(0, 100)
            } catch (_: Exception) {
                50
            }
        }
    }

    fun setScreenBrightness(activity: Activity, percent: Int) {
        val layoutParams = activity.window.attributes
        layoutParams.screenBrightness = (percent.coerceIn(1, 100) / 100f)
        activity.window.attributes = layoutParams
    }

    fun getVolume(context: Context): Int {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return 50
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        return ((current.toFloat() / max.toFloat()) * 100f).roundToInt().coerceIn(0, 100)
    }

    fun setVolume(context: Context, percent: Int) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val target = ((percent.coerceIn(0, 100) / 100f) * max).roundToInt().coerceIn(0, max)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
    }
}
