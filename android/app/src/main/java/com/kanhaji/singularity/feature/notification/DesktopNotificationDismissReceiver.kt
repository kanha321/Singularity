package com.kanhaji.singularity.feature.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.kanhaji.singularity.feature.notification.model.NotificationDismiss

class DesktopNotificationDismissReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_DISMISS = "com.kanhaji.singularity.action.DESKTOP_NOTIF_DISMISSED"
        const val EXTRA_NOTIF_ID = "extra_notif_id"
        private const val TAG = "DesktopNotifDismiss"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == ACTION_DISMISS) {
            val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, 0)
            Log.d(TAG, "Dismiss broadcast received for ID: $notifId")
            if (notifId > 0) {
                PhoneNotificationTracker.enqueueDismissal(
                    NotificationDismiss(id = notifId.toString(), source = "phone")
                )
            }
        }
    }
}
