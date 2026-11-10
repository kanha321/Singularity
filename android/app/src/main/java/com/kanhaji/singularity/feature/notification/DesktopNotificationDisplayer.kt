package com.kanhaji.singularity.feature.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.kanhaji.singularity.R
import com.kanhaji.singularity.feature.notification.model.DesktopNotification

object DesktopNotificationDisplayer {
    const val CHANNEL_ID = "desktop_notifications"
    private const val CHANNEL_NAME = "Desktop Notifications"
    private const val CHANNEL_DESC = "Notifications forwarded from Linux Desktop"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = CHANNEL_DESC
                    enableVibration(true)
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    fun show(context: Context, notif: DesktopNotification) {
        ensureChannel(context)

        val dismissIntent = Intent(context, DesktopNotificationDismissReceiver::class.java).apply {
            action = DesktopNotificationDismissReceiver.ACTION_DISMISS
            putExtra(DesktopNotificationDismissReceiver.EXTRA_NOTIF_ID, notif.id)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val deletePendingIntent = PendingIntent.getBroadcast(
            context,
            notif.id,
            dismissIntent,
            flags
        )

        val title = if (notif.summary.isNotEmpty()) notif.summary else notif.appName
        val subText = if (notif.summary.isNotEmpty()) notif.appName else "Linux Desktop"

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(notif.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notif.body))
            .setSubText(subText)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setDeleteIntent(deletePendingIntent)

        val nm = NotificationManagerCompat.from(context)
        try {
            nm.notify(notif.id, builder.build())
        } catch (_: SecurityException) {}
    }

    fun cancel(context: Context, id: Int) {
        val nm = NotificationManagerCompat.from(context)
        nm.cancel(id)
    }
}

