package com.kanhaji.singularity.feature.player.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VideoFileUtilsTest {

    @Test
    fun isVideoFile_withSupportedExtensions_returnsTrue() {
        assertThat(VideoFileUtils.isVideoFile("movie.mp4")).isTrue()
        assertThat(VideoFileUtils.isVideoFile("anime.MKV")).isTrue()
        assertThat(VideoFileUtils.isVideoFile("clip.webm")).isTrue()
        assertThat(VideoFileUtils.isVideoFile("recording.avi")).isTrue()
        assertThat(VideoFileUtils.isVideoFile("camera.MOV")).isTrue()
        assertThat(VideoFileUtils.isVideoFile("stream.ts")).isTrue()
        assertThat(VideoFileUtils.isVideoFile("video.m4v")).isTrue()
    }

    @Test
    fun isVideoFile_withNonVideoExtensions_returnsFalse() {
        assertThat(VideoFileUtils.isVideoFile("document.pdf")).isFalse()
        assertThat(VideoFileUtils.isVideoFile("photo.jpg")).isFalse()
        assertThat(VideoFileUtils.isVideoFile("archive.zip")).isFalse()
        assertThat(VideoFileUtils.isVideoFile("track.mp3")).isFalse()
        assertThat(VideoFileUtils.isVideoFile("no_extension")).isFalse()
    }

    @Test
    fun buildStreamingUrl_withPath_encodesCorrectly() {
        val url = VideoFileUtils.buildStreamingUrl(
            baseUrl = "http://192.168.1.5:8920",
            remotePath = "/home/kanhaji/Videos/My Video (1).mp4"
        )
        assertThat(url).startsWith("http://192.168.1.5:8920/api/fs/raw?path=")
        assertThat(url).contains("My+Video+%281%29.mp4")
    }
}
