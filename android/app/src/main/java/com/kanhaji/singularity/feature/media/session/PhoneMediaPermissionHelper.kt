package com.kanhaji.singularity.feature.media.session

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

object PhoneMediaPermissionHelper {

    fun isNotificationAccessGranted(context: Context): Boolean {
        val enabledPackages = NotificationManagerCompat.getEnabledListenerPackages(context)
        return enabledPackages.contains(context.packageName)
    }

    fun getSettingsAction(sdkInt: Int = Build.VERSION.SDK_INT): String {
        return if (sdkInt >= Build.VERSION_CODES.R) {
            Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS
        } else {
            Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
        }
    }

    fun createSettingsIntent(context: Context, sdkInt: Int = Build.VERSION.SDK_INT): Intent {
        val action = getSettingsAction(sdkInt)
        return Intent(action).apply {
            if (sdkInt >= Build.VERSION_CODES.R) {
                val cn = ComponentName(context, SingularityMediaListenerService::class.java)
                putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, cn.flattenToString())
            }
        }
    }
}
