package com.kanhaji.singularity.feature.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShareReceiverActivityTest {

    @Test
    fun extractUrl_extracts_standalone_url() {
        val url = "https://github.com/torvalds/linux"
        assertEquals(url, ShareReceiverActivity.extractUrl(url))
    }

    @Test
    fun extractUrl_extracts_url_from_complex_message() {
        val message = "Check out this awesome video: https://youtu.be/dQw4w9WgXcQ it's great!"
        assertEquals("https://youtu.be/dQw4w9WgXcQ", ShareReceiverActivity.extractUrl(message))
    }

    @Test
    fun extractUrl_returns_null_for_plain_text_without_url() {
        assertNull(ShareReceiverActivity.extractUrl("just random text without links"))
    }
}

