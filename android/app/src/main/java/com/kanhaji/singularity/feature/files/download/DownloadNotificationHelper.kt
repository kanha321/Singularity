package com.kanhaji.singularity.feature.files.download

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.kanhaji.singularity.R

class DownloadNotificationHelper(
    private val context: Context,
    private val notificationManager: NotificationManager
) {
    companion object {
        const val NOTIFICATION_ID = 4040

        fun formatTitle(folderName: String, isResuming: Boolean = false): String {
            val prefix = if (isResuming) "Resuming" else "Downloading"
            return "$prefix $folderName"
        }

        fun formatSubtitle(
            copiedFiles: Int,
            totalFiles: Int,
            downloadedBytes: Long,
            totalBytes: Long,
            pct: Int
        ): String {
            val filesPart = "$copiedFiles/$totalFiles files"
            val bytesPart = "${FileDownloadUtils.formatBytes(downloadedBytes)} / ${FileDownloadUtils.formatBytes(totalBytes)}"
            return "$filesPart • $bytesPart ($pct%)"
        }

        fun formatReconnectingTitle(folderName: String, attempt: Int, maxRetries: Int): String {
            return "Reconnecting ($attempt/$maxRetries): $folderName"
        }

        fun formatCompletionSubtitle(totalFiles: Int, totalBytes: Long, skipped: Int): String {
            val bytesFormatted = FileDownloadUtils.formatBytes(totalBytes)
            val newlySaved = (totalFiles - skipped).coerceAtLeast(0)
            return when {
                skipped > 0 && newlySaved > 0 -> "$totalFiles files ($bytesFormatted) • Saved $newlySaved new, $skipped unchanged"
                skipped > 0 && newlySaved == 0 -> "All $skipped files ($bytesFormatted) are already up to date"
                else -> "$totalFiles files • $bytesFormatted saved to Download/Singularity"
            }
        }
    }

    init {
        DownloadNotificationChannels.createChannels(notificationManager)
    }

    fun buildScanningNotification(text: String): Notification {
        return NotificationCompat.Builder(context, DownloadNotificationChannels.PROGRESS_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Preparing Download")
            .setContentText(text)
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun updateProgress(
        folderName: String,
        copiedFiles: Int,
        totalFiles: Int,
        bytes: Long,
        totalBytes: Long,
        pct: Int,
        isResuming: Boolean = false
    ) {
        val title = formatTitle(folderName, isResuming)
        val subtitle = formatSubtitle(copiedFiles, totalFiles, bytes, totalBytes, pct)
        val notif = NotificationCompat.Builder(context, DownloadNotificationChannels.PROGRESS_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setProgress(100, pct, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        notificationManager.notify(NOTIFICATION_ID, notif)
    }

    fun updateReconnecting(
        folderName: String,
        copiedFiles: Int,
        totalFiles: Int,
        attempt: Int,
        maxRetries: Int,
        pct: Int
    ) {
        val title = formatReconnectingTitle(folderName, attempt, maxRetries)
        val subtitle = "Paused at $pct% ($copiedFiles/$totalFiles files) • Retrying in 2s..."
        val notif = NotificationCompat.Builder(context, DownloadNotificationChannels.PROGRESS_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setProgress(100, pct, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        notificationManager.notify(NOTIFICATION_ID, notif)
    }

    fun showCompletion(
        folderName: String,
        successCount: Int,
        totalCount: Int,
        totalBytes: Long,
        skippedCount: Int = 0
    ) {
        val allSucceeded = successCount == totalCount
        val title = if (allSucceeded) "Download Complete: $folderName" else "Download Incomplete: $folderName"
        val subtitle = formatCompletionSubtitle(totalCount, totalBytes, skippedCount)
        val icon = if (allSucceeded) android.R.drawable.stat_sys_download_done else R.drawable.ic_launcher_foreground

        val notif = NotificationCompat.Builder(context, DownloadNotificationChannels.COMPLETE_CHANNEL_ID)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        notificationManager.notify(NOTIFICATION_ID + 1, notif)
    }
}
