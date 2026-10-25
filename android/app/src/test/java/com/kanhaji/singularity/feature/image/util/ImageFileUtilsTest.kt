package com.kanhaji.singularity.feature.image.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageFileUtilsTest {

    @Test
    fun isImageFile_standardExtensions_returnsTrue() {
        assertTrue(ImageFileUtils.isImageFile("photo.jpg"))
        assertTrue(ImageFileUtils.isImageFile("photo.JPEG"))
        assertTrue(ImageFileUtils.isImageFile("screenshot.png"))
        assertTrue(ImageFileUtils.isImageFile("graphic.webp"))
        assertTrue(ImageFileUtils.isImageFile("meme.gif"))
        assertTrue(ImageFileUtils.isImageFile("vector.svg"))
        assertTrue(ImageFileUtils.isImageFile("bitmap.bmp"))
        assertTrue(ImageFileUtils.isImageFile("camera.heic"))
    }

    @Test
    fun isImageFile_nonImageExtensions_returnsFalse() {
        assertFalse(ImageFileUtils.isImageFile("movie.mp4"))
        assertFalse(ImageFileUtils.isImageFile("track.mp3"))
        assertFalse(ImageFileUtils.isImageFile("archive.zip"))
        assertFalse(ImageFileUtils.isImageFile("document.pdf"))
        assertFalse(ImageFileUtils.isImageFile("noextension"))
    }

    @Test
    fun buildImageUrl_encodesSpecialCharacters() {
        val url = ImageFileUtils.buildImageUrl("/home/user/Pictures/Sunset & Beach 2026.png")
        assertTrue(url.contains("/api/fs/raw?path="))
        assertTrue(url.contains("Sunset+%26+Beach+2026.png") || url.contains("Sunset%20%26%20Beach%202026.png"))
    }
}
