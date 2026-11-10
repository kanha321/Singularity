package com.kanhaji.singularity.feature.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.app.NotificationCompat

class TestNotificationReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "TestNotificationReceiver"
        const val ACTION_TEST_NOTIF = "com.kanhaji.singularity.TEST_NOTIFICATION"
        const val ACTION_RESULT = "com.kanhaji.singularity.TEST_ACTION_RESULT"
        private const val CHANNEL_ID = "singularity_test_channel"
        private const val NOTIF_ID = 9999
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_TEST_NOTIF -> handlePostTest(context, intent)
            ACTION_RESULT -> handleActionResult(context, intent)
        }
    }

    private fun handlePostTest(context: Context, intent: Intent) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Test Channel", NotificationManager.IMPORTANCE_HIGH)
            nm.createNotificationChannel(channel)
        }

        val title = intent.getStringExtra("title") ?: "Rahul"
        val text = intent.getStringExtra("text") ?: "Hey! Are you free?"
        val app = intent.getStringExtra("app") ?: "WhatsApp"

        // Action 1: Mark as read
        val markReadIntent = Intent(context, TestNotificationReceiver::class.java).apply {
            action = ACTION_RESULT
            putExtra("action_name", "Mark as Read")
        }
        val markReadPending = PendingIntent.getBroadcast(
            context, 101, markReadIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        // Action 2: Mute
        val muteIntent = Intent(context, TestNotificationReceiver::class.java).apply {
            action = ACTION_RESULT
            putExtra("action_name", "Mute")
        }
        val mutePending = PendingIntent.getBroadcast(
            context, 103, muteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        // Action 3: Reply with RemoteInput
        val replyIntent = Intent(context, TestNotificationReceiver::class.java).apply {
            action = ACTION_RESULT
            putExtra("action_name", "Reply")
        }
        val replyPending = PendingIntent.getBroadcast(
            context, 102, replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val remoteInput = androidx.core.app.RemoteInput.Builder("key_reply_text")
            .setLabel("Reply to $title...")
            .build()

        val replyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send, "Reply", replyPending
        ).addRemoteInput(remoteInput).build()

        val markReadAction = NotificationCompat.Action.Builder(
            android.R.drawable.checkbox_on_background, "Mark as Read", markReadPending
        ).build()

        val muteAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_lock_silent_mode, "Mute", mutePending
        ).build()

        val extras = Bundle().apply {
            putBoolean("singularity_test_notif", true)
            putString("custom_app_name", app)
        }

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setContentText(text)
            .addExtras(extras)
            .addAction(markReadAction)
            .addAction(muteAction)
            .addAction(replyAction)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        nm.notify(NOTIF_ID, notif)
        Log.i(TAG, "Posted test notification: title=$title text=$text")
    }

    private fun handleActionResult(context: Context, intent: Intent) {
        val actionName = intent.getStringExtra("action_name") ?: "unknown"
        val remoteInputBundle = androidx.core.app.RemoteInput.getResultsFromIntent(intent)
        val replyText = remoteInputBundle?.getCharSequence("key_reply_text")?.toString()

        if (replyText != null) {
            Log.i(TAG, "🎉 RECEIVED INLINE REPLY: '$replyText' (action=$actionName)")
        } else {
            Log.i(TAG, "⚡ EXECUTED ONE-TAP ACTION: '$actionName'")
        }

        // Auto-dismiss the test notification
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIF_ID)
    }
}

