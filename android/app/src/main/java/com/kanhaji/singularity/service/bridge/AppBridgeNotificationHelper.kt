package com.kanhaji.singularity.service.bridge

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.kanhaji.singularity.MainActivity
import com.kanhaji.singularity.R
import com.kanhaji.singularity.biometric.BiometricAuthActivity
import com.kanhaji.singularity.feature.auth.model.AuthMode

object AppBridgeNotificationHelper {
    const val CHANNEL_ID = "singularity_highway_channel"
    const val AUTH_CHANNEL_ID = "singularity_auth_alerts_v6"
    const val LOCK_STATUS_CHANNEL_ID = "singularity_lock_status_v2"
    const val NOTIFICATION_ID = 2828
    const val AUTH_NOTIFICATION_ID = 2829
    const val PC_LOCKED_NOTIFICATION_ID = 2830

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val highway = NotificationChannel(CHANNEL_ID, "Singularity Highway", NotificationManager.IMPORTANCE_LOW)
        val auth = NotificationChannel(AUTH_CHANNEL_ID, "Singularity Authentication", NotificationManager.IMPORTANCE_HIGH).apply {
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setBypassDnd(true)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 200, 100, 200)
            val audio = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT)
                .build()
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), audio)
        }
        val lock = NotificationChannel(LOCK_STATUS_CHANNEL_ID, "Singularity PC Lock Status", NotificationManager.IMPORTANCE_LOW)
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(highway)
        manager.createNotificationChannel(auth)
        manager.createNotificationChannel(lock)
    }

    fun buildForegroundNotification(context: Context, connected: Boolean): Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(context, 100, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val text = if (connected) "🟢 Connected to Arch Linux (Desktop)" else "⚪ Highway listening on localabstract:singularity_app"
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Singularity Convergence")
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pending)
            .build()
    }

    fun updateHighwayNotification(context: Context, connected: Boolean) {
        try {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager?.notify(NOTIFICATION_ID, buildForegroundNotification(context, connected))
        } catch (_: Exception) {}
    }

    fun showPcLockedNotification(context: Context, isLogin: Boolean = false, fullScreen: Boolean = false) {
        try {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            val title = if (isLogin) "Singularity Greeter" else "Singularity Lockscreen"
            val action = if (isLogin) "Log In to PC Session" else "Unlock PC Screen"
            val intent = Intent(context, BiometricAuthActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(BiometricAuthActivity.EXTRA_TITLE, title)
                putExtra(BiometricAuthActivity.EXTRA_REASON, action)
                if (isLogin) putExtra(BiometricAuthActivity.EXTRA_IS_LOGIN, true) else putExtra(BiometricAuthActivity.EXTRA_IS_UNLOCK, true)
            }
            val reqCode = if (isLogin) 104 else 103
            val pending = PendingIntent.getActivity(context, reqCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val notif = NotificationCompat.Builder(context, AUTH_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(if (isLogin) "💻 PC Login Screen" else "💻 PC Screen Locked")
                .setContentText(if (isLogin) "Tap to log in with fingerprint" else "Tap to unlock laptop with fingerprint")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setContentIntent(pending)
                .addAction(R.mipmap.ic_launcher, if (isLogin) "🔑 Log In with Fingerprint" else "🔓 Unlock with Fingerprint", pending)
                .apply { if (fullScreen) setFullScreenIntent(pending, true) }
                .build()
            manager.notify(PC_LOCKED_NOTIFICATION_ID, notif)
        } catch (_: Exception) {}
    }

    fun dismissPcLockedNotification(context: Context) {
        try { context.getSystemService(NotificationManager::class.java)?.cancel(PC_LOCKED_NOTIFICATION_ID) } catch (_: Exception) {}
    }

    fun showAuthRequestNotification(
        context: Context,
        reason: String,
        timeoutSecs: Long,
        mode: AuthMode,
        authPending: PendingIntent,
        rejectPending: PendingIntent
    ) {
        try {
            val builder = NotificationCompat.Builder(context, AUTH_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Singularity Authentication Request")
                .setContentText(reason)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setVibrate(longArrayOf(0, 200, 100, 200))
                .setContentIntent(authPending)
                .setAutoCancel(true)
                .setTimeoutAfter(timeoutSecs * 1000L)
                .addAction(R.mipmap.ic_launcher, "Verify with Fingerprint", authPending)
                .addAction(R.mipmap.ic_launcher, "Use Password", rejectPending)
            if (mode == AuthMode.PROMPT) {
                builder.setFullScreenIntent(authPending, true)
            }
            context.getSystemService(NotificationManager::class.java)?.notify(AUTH_NOTIFICATION_ID, builder.build())
        } catch (_: Exception) {}
    }

    fun cancelAuthNotification(context: Context) {
        try { context.getSystemService(NotificationManager::class.java)?.cancel(AUTH_NOTIFICATION_ID) } catch (_: Exception) {}
    }
}

