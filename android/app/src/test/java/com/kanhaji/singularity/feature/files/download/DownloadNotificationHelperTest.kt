package com.kanhaji.singularity.feature.files.download

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadNotificationHelperTest {

    @Test
    fun formatTitle_folderOrFileName_formatsPrefixAccurately() {
        val titleNormal = DownloadNotificationHelper.formatTitle("Photos", isResuming = false)
        val titleResuming = DownloadNotificationHelper.formatTitle("Photos", isResuming = true)

        assertEquals("Downloading Photos", titleNormal)
        assertEquals("Resuming Photos", titleResuming)
    }

    @Test
    fun formatSubtitle_filesCopiedAndTotalFolderSize_formatsAccurately() {
        val subtitle = DownloadNotificationHelper.formatSubtitle(
            copiedFiles = 14,
            totalFiles = 45,
            downloadedBytes = 44564480L,
            totalBytes = 157286400L,
            pct = 28
        )

        assertEquals("14/45 files • 42.5 MB / 150.0 MB (28%)", subtitle)
    }

    @Test
    fun formatReconnectingTitle_attemptAndMax_formatsProperly() {
        val title = DownloadNotificationHelper.formatReconnectingTitle("Music", 2, 3)

        assertEquals("Reconnecting (2/3): Music", title)
    }

    @Test
    fun formatCompletionSubtitle_folderSummary_formatsAccurately() {
        assertEquals(
            "10 files • 100.0 MB saved to Download/Singularity",
            DownloadNotificationHelper.formatCompletionSubtitle(totalFiles = 10, totalBytes = 104857600L, skipped = 0)
        )
        assertEquals(
            "10 files (100.0 MB) • Saved 8 new, 2 unchanged",
            DownloadNotificationHelper.formatCompletionSubtitle(totalFiles = 10, totalBytes = 104857600L, skipped = 2)
        )
        assertEquals(
            "All 5 files (50.0 MB) are already up to date",
            DownloadNotificationHelper.formatCompletionSubtitle(totalFiles = 5, totalBytes = 52428800L, skipped = 5)
        )
    }
}
