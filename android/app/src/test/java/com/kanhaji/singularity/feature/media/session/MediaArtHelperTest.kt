package com.kanhaji.singularity.feature.media.session

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

class MediaArtHelperTest {

    @Before
    fun setUp() {
        MediaArtHelper.clearCache()
    }

    @Test
    fun extractArt_withHttpUri_returnsUriAndNoBase64() {
        val (uri, b64) = MediaArtHelper.extractArt(
            uri = "https://img.youtube.com/vi/123/default.jpg",
            hasBitmap = false,
            title = "Title",
            artist = "Artist"
        ) { null }

        assertThat(uri).isEqualTo("https://img.youtube.com/vi/123/default.jpg")
        assertThat(b64).isNull()
    }

    @Test
    fun extractArt_withNoBitmapOrUri_returnsNulls() {
        val (uri, b64) = MediaArtHelper.extractArt(
            uri = null,
            hasBitmap = false,
            title = "Title",
            artist = "Artist"
        ) { null }

        assertThat(uri).isNull()
        assertThat(b64).isNull()
    }

    @Test
    fun extractArt_withBitmap_encodesBase64AndCaches() {
        var compressCallCount = 0
        val fakeCompress: () -> ByteArray? = {
            compressCallCount++
            byteArrayOf(1, 2, 3, 4)
        }

        // First call compresses
        val (uri1, b64_1) = MediaArtHelper.extractArt(
            uri = null,
            hasBitmap = true,
            title = "Song",
            artist = "Artist",
            compressFn = fakeCompress
        )
        assertThat(uri1).isNull()
        assertThat(b64_1).isEqualTo("AQIDBA==")
        assertThat(compressCallCount).isEqualTo(1)

        // Second call with same title/artist reuses cache without calling compressFn
        val (uri2, b64_2) = MediaArtHelper.extractArt(
            uri = null,
            hasBitmap = true,
            title = "Song",
            artist = "Artist",
            compressFn = fakeCompress
        )
        assertThat(b64_2).isEqualTo("AQIDBA==")
        assertThat(compressCallCount).isEqualTo(1)
    }

    @Test
    fun clearCache_resetsCachedBase64() {
        var count = 0
        val fakeCompress: () -> ByteArray? = {
            count++
            byteArrayOf(10, 20)
        }

        MediaArtHelper.extractArt(null, true, "Song", "Artist", fakeCompress)
        assertThat(count).isEqualTo(1)

        MediaArtHelper.clearCache()

        MediaArtHelper.extractArt(null, true, "Song", "Artist", fakeCompress)
        assertThat(count).isEqualTo(2)
    }
}
