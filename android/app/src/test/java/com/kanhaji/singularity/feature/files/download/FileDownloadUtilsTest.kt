package com.kanhaji.singularity.feature.files.download

import org.junit.Assert.assertEquals
import org.junit.Test

class FileDownloadUtilsTest {

    @Test
    fun formatBytes_variousSizes_formatsAccurately() {
        assertEquals("0 B", FileDownloadUtils.formatBytes(0))
        assertEquals("1.0 KB", FileDownloadUtils.formatBytes(1024))
        assertEquals("500.0 KB", FileDownloadUtils.formatBytes(500 * 1024))
        assertEquals("1.5 MB", FileDownloadUtils.formatBytes((1.5 * 1024 * 1024).toLong()))
        assertEquals("2.00 GB", FileDownloadUtils.formatBytes(2L * 1024 * 1024 * 1024))
    }

    @Test
    fun calculatePercentage_standardValues_calculatesCorrectly() {
        assertEquals(0, FileDownloadUtils.calculatePercentage(0, 100))
        assertEquals(50, FileDownloadUtils.calculatePercentage(50, 100))
        assertEquals(100, FileDownloadUtils.calculatePercentage(100, 100))
        assertEquals(100, FileDownloadUtils.calculatePercentage(150, 100))
        assertEquals(0, FileDownloadUtils.calculatePercentage(50, 0))
    }

    @Test
    fun formatRangeHeader_positiveOffset_returnsHeader() {
        assertEquals("bytes=1024-", FileDownloadUtils.formatRangeHeader(1024))
        assertEquals("bytes=500000000-", FileDownloadUtils.formatRangeHeader(500000000L))
        assertEquals(null, FileDownloadUtils.formatRangeHeader(0))
        assertEquals(null, FileDownloadUtils.formatRangeHeader(-10))
    }

    @Test
    fun parseTotalFromContentRange_validAndInvalidRanges_parsesCorrectly() {
        assertEquals(1000L, FileDownloadUtils.parseTotalFromContentRange("bytes 0-499/1000"))
        assertEquals(52428800L, FileDownloadUtils.parseTotalFromContentRange("bytes 10485760-52428799/52428800"))
        assertEquals(null, FileDownloadUtils.parseTotalFromContentRange("bytes 0-499/*"))
        assertEquals(null, FileDownloadUtils.parseTotalFromContentRange(null))
        assertEquals(null, FileDownloadUtils.parseTotalFromContentRange(""))
    }
}

