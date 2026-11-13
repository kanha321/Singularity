package com.kanhaji.singularity.feature.find

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object DeviceRinger {
    private const val TAG = "SingularityDeviceRinger"
    private const val CHANNEL_ID = "singularity_find_device"
    private const val NOTIFICATION_ID = 2835
    const val ACTION_STOP_RINGING = "com.kanhaji.singularity.action.STOP_RINGING"

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var previousAlarmVolume: Int? = null
    private var previousRingerMode: Int? = null
    private var lastDismissedAt = 0L

    private val _isRinging = MutableStateFlow(false)
    val isRinging = _isRinging.asStateFlow()

    @Synchronized
    fun start(context: Context, force: Boolean = false) {
        if (_isRinging.value) {
            Log.d(TAG, "DeviceRinger already active")
            return
        }
        if (!force && System.currentTimeMillis() - lastDismissedAt < 3000L) {
            Log.d(TAG, "DeviceRinger start ignored: dismissed within last 3s cooldown")
            return
        }
        Log.i(TAG, "Starting Find My Device alarm & siren at 100% volume (overriding DND)...")

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (audioManager != null) {
            try {
                previousAlarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
                previousRingerMode = audioManager.ringerMode
                val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)
                Log.d(TAG, "Alarm stream volume set to max: $maxVolume (previous: $previousAlarmVolume)")
            } catch (e: Exception) {
                Log.w(TAG, "Failed setting alarm volume: ${e.message}")
            }
        }

        // 1. Wake lock
        try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                "singularity:find_device_wake"
            )?.apply {
                acquire(10 * 60 * 1000L) // 10 minute safety timeout
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to acquire wake lock: ${e.message}")
        }

        // 2. Play Alarm Audio via MediaPlayer with USAGE_ALARM to punch through DND
        try {
            val alertUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                .build()

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(attrs)
                setDataSource(context, alertUri)
                isLooping = true
                prepare()
                start()
            }
            Log.i(TAG, "MediaPlayer siren active looping on $alertUri")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start MediaPlayer siren: ${e.message}", e)
        }

        // 3. Trigger continuous vibration pattern
        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            val pattern = longArrayOf(0, 800, 400, 800, 400)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createWaveform(pattern, 0)
                val audioAttrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .build()
                vibrator?.vibrate(effect, audioAttrs)
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to trigger vibration: ${e.message}")
        }

        // 4. Show Notification & Full Screen Intent
        showNotification(context)

        // 5. Launch Full Screen Alert Activity
        try {
            val alertIntent = Intent(context, FindDeviceAlertActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            context.startActivity(alertIntent)
        } catch (e: Exception) {
            Log.w(TAG, "Failed launching FindDeviceAlertActivity: ${e.message}")
        }

        _isRinging.value = true
    }

    @Synchronized
    fun stop(context: Context) {
        lastDismissedAt = System.currentTimeMillis()
        if (!_isRinging.value) return
        Log.i(TAG, "Stopping Find My Device alarm and restoring previous audio state...")

        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping media player: ${e.message}")
        }
        mediaPlayer = null

        try {
            vibrator?.cancel()
        } catch (e: Exception) {
            Log.w(TAG, "Error cancelling vibrator: ${e.message}")
        }
        vibrator = null

        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing wake lock: ${e.message}")
        }
        wakeLock = null

        // Restore original audio state
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (audioManager != null) {
            try {
                previousAlarmVolume?.let { audioManager.setStreamVolume(AudioManager.STREAM_ALARM, it, 0) }
                previousRingerMode?.let { audioManager.ringerMode = it }
                Log.d(TAG, "Restored previous volume ($previousAlarmVolume) and ringer mode ($previousRingerMode)")
            } catch (e: Exception) {
                Log.w(TAG, "Failed restoring audio state: ${e.message}")
            }
        }
        previousAlarmVolume = null
        previousRingerMode = null

        // Dismiss notification & finish activity
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(NOTIFICATION_ID)
        } catch (_: Exception) {}

        FindDeviceAlertActivity.dismissActive()
        _isRinging.value = false
    }

    private fun showNotification(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Find My Device Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High-priority alerts when locating your device from PC"
                enableVibration(true)
                setSound(null, null) // Audio is handled by MediaPlayer
            }
            nm.createNotificationChannel(channel)
        }

        val stopIntent = Intent(context, FindDeviceStopReceiver::class.java).apply {
            action = ACTION_STOP_RINGING
        }
        val stopPending = PendingIntent.getBroadcast(
            context,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val fullScreenIntent = Intent(context, FindDeviceAlertActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val fullScreenPending = PendingIntent.getActivity(
            context,
            1,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("🔔 Finding this device")
            .setContentText("Siren is playing at 100% volume. Tap to stop.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(fullScreenPending)
            .setFullScreenIntent(fullScreenPending, true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Ringing", stopPending)
            .build()

        nm.notify(NOTIFICATION_ID, notif)
    }

    class FindDeviceStopReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_STOP_RINGING) {
                stop(context)
            }
        }
    }
}

