package com.kanhaji.singularity.feature.files.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileDownloadManagerTest {

    @Test
    fun updateProgress_updatesStateFlowCorrectly() {
        FileDownloadManager.updateProgress(
            name = "video.mp4",
            index = 0,
            total = 1,
            bytes = 5000L,
            totalBytes = 10000L,
            pct = 50,
            running = true
        )

        val current = FileDownloadManager.progress.value
        assertEquals("video.mp4", current.currentFileName)
        assertEquals(50, current.percentage)
        assertEquals(5000L, current.bytesDownloaded)
        assertEquals(10000L, current.totalBytes)
        assertTrue(current.isRunning)
        assertFalse(current.isCompleted)
    }

    @Test
    fun complete_setsRunningFalseAndCompletedTrue() {
        FileDownloadManager.complete(success = 3, total = 3)

        val current = FileDownloadManager.progress.value
        assertFalse(current.isRunning)
        assertTrue(current.isCompleted)
        assertEquals(3, current.currentIndex)
        assertEquals(3, current.totalFiles)
    }

    @Test
    fun updateProgress_withResuming_flagsResumingTrue() {
        FileDownloadManager.updateProgress(
            name = "iso.img",
            index = 1,
            total = 2,
            bytes = 2000L,
            totalBytes = 4000L,
            pct = 50,
            running = true,
            isResuming = true
        )

        val current = FileDownloadManager.progress.value
        assertTrue(current.isResuming)
        assertFalse(current.isReconnecting)
        assertEquals("iso.img", current.currentFileName)
    }

    @Test
    fun updateReconnecting_setsReconnectingFlag() {
        FileDownloadManager.updateReconnecting(
            name = "archive.tar",
            index = 0,
            total = 1,
            pct = 30
        )

        val current = FileDownloadManager.progress.value
        assertTrue(current.isReconnecting)
        assertTrue(current.isRunning)
        assertEquals("archive.tar", current.currentFileName)
        assertEquals(30, current.percentage)
    }
}

