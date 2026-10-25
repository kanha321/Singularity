package com.kanhaji.singularity.feature.files.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

object DownloadNotificationChannels {
    const val PROGRESS_CHANNEL_ID = "singularity_downloads_progress_v2"
    const val COMPLETE_CHANNEL_ID = "singularity_downloads_complete_v2"

    fun createChannels(notificationManager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val progressChan = NotificationChannel(
                PROGRESS_CHANNEL_ID,
                "File Download Progress",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows real-time progress while files are downloading"
            }
            val completeChan = NotificationChannel(
                COMPLETE_CHANNEL_ID,
                "Download Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifies when downloads finish or fail"
            }
            notificationManager.createNotificationChannel(progressChan)
            notificationManager.createNotificationChannel(completeChan)
        }
    }
}
