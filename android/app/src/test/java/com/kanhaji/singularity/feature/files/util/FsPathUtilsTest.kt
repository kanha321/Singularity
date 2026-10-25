package com.kanhaji.singularity.feature.files.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FsPathUtilsTest {

    @Test
    fun formatFolderTitle_emptyOrTilde_returnsHome() {
        assertThat(formatFolderTitle("")).isEqualTo("Home")
        assertThat(formatFolderTitle("~")).isEqualTo("Home")
    }

    @Test
    fun formatFolderTitle_rootSlash_returnsRoot() {
        assertThat(formatFolderTitle("/")).isEqualTo("Root")
    }

    @Test
    fun formatFolderTitle_nestedPath_returnsLeafFolderName() {
        assertThat(formatFolderTitle("/home/kanhaji/Downloads")).isEqualTo("Downloads")
        assertThat(formatFolderTitle("~/Projects/singularity")).isEqualTo("singularity")
    }

    @Test
    fun formatFolderTitle_trailingSlash_trimsAndReturnsLeafName() {
        assertThat(formatFolderTitle("/var/log/")).isEqualTo("log")
    }

    @Test
    fun buildThumbnailUrl_regularFile_returnsThumbnailEndpoint() {
        val url = buildThumbnailUrl("http://127.0.0.1:8920", "/home/kanhaji/Pictures/photo.jpg")
        assertThat(url).isEqualTo("http://127.0.0.1:8920/api/fs/thumbnail?path=%2Fhome%2Fkanhaji%2FPictures%2Fphoto.jpg")
    }

    @Test
    fun buildThumbnailUrl_archiveVirtualPath_returnsNull() {
        val url = buildThumbnailUrl("http://127.0.0.1:8920", "/home/kanhaji/archive.zip!/image.png")
        assertThat(url).isNull()
    }
}
