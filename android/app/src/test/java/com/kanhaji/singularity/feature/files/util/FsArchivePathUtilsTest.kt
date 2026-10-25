package com.kanhaji.singularity.feature.files.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FsArchivePathUtilsTest {

    @Test
    fun isArchive_withSupportedArchiveExtensions_returnsTrue() {
        assertThat(isArchive("backup.zip")).isTrue()
        assertThat(isArchive("archive.7z")).isTrue()
        assertThat(isArchive("source.tar.gz")).isTrue()
        assertThat(isArchive("linux.tar.xz")).isTrue()
        assertThat(isArchive("media.rar")).isTrue()
        assertThat(isArchive("distro.iso")).isTrue()
        assertThat(isArchive("package.zst")).isTrue()
    }

    @Test
    fun isArchive_withRegularFiles_returnsFalse() {
        assertThat(isArchive("main.rs")).isFalse()
        assertThat(isArchive("image.png")).isFalse()
        assertThat(isArchive("document.pdf")).isFalse()
        assertThat(isArchive("folder")).isFalse()
    }

    @Test
    fun isArchivePath_withVirtualDelimiter_returnsTrue() {
        assertThat(isArchivePath("/home/user/project.zip!/")).isTrue()
        assertThat(isArchivePath("/home/user/project.zip!/src/main.rs")).isTrue()
        assertThat(isArchivePath("/home/user/project.zip")).isFalse()
        assertThat(isArchivePath("/home/user/normal_folder")).isFalse()
    }

    @Test
    fun splitArchivePath_withRootVirtualPath_returnsArchiveAndEmptyInternal() {
        val (archive, internal) = splitArchivePath("/home/user/project.zip!/")
        assertThat(archive).isEqualTo("/home/user/project.zip")
        assertThat(internal).isEqualTo("")
    }

    @Test
    fun splitArchivePath_withNestedVirtualPath_returnsArchiveAndInternalSubpath() {
        val (archive, internal) = splitArchivePath("/home/user/project.zip!/src/components")
        assertThat(archive).isEqualTo("/home/user/project.zip")
        assertThat(internal).isEqualTo("src/components")
    }

    @Test
    fun formatFolderTitle_withVirtualArchivePath_returnsCleanFolderTitle() {
        assertThat(formatFolderTitle("/home/user/project.zip!/")).isEqualTo("project.zip")
        assertThat(formatFolderTitle("/home/user/project.zip!/src")).isEqualTo("src")
        assertThat(formatFolderTitle("/home/user/project.zip!/src/components/")).isEqualTo("components")
    }

    @Test
    fun buildRawFileUrl_normalPath_returnsStandardRawEndpoint() {
        val url = buildRawFileUrl("http://127.0.0.1:8920", "/home/user/document.pdf")
        assertThat(url).isEqualTo("http://127.0.0.1:8920/api/fs/raw?path=%2Fhome%2Fuser%2Fdocument.pdf")
    }

    @Test
    fun buildRawFileUrl_archiveVirtualPath_returnsArchiveRawEndpoint() {
        val url = buildRawFileUrl("http://127.0.0.1:8920", "/home/user/code.zip!/src/main.rs")
        assertThat(url).isEqualTo("http://127.0.0.1:8920/api/fs/archive/raw?path=%2Fhome%2Fuser%2Fcode.zip&entry=src%2Fmain.rs")
    }

    @Test
    fun buildChildPath_rootDirectory_returnsLeadingSlashChild() {
        assertThat(buildChildPath("/", "subfolder")).isEqualTo("/subfolder")
    }

    @Test
    fun buildChildPath_rootVirtualArchive_returnsChildInVirtualRoot() {
        assertThat(buildChildPath("/home/user/code.zip!/", "new_dir")).isEqualTo("/home/user/code.zip!/new_dir")
        assertThat(buildChildPath("/home/user/code.zip!", "new_dir")).isEqualTo("/home/user/code.zip!/new_dir")
    }

    @Test
    fun buildChildPath_nestedVirtualArchive_returnsChildInVirtualSubfolder() {
        assertThat(buildChildPath("/home/user/code.zip!/src", "main.rs")).isEqualTo("/home/user/code.zip!/src/main.rs")
    }

    @Test
    fun buildChildPath_standardDirectory_returnsChild() {
        assertThat(buildChildPath("/home/user/Projects", "app")).isEqualTo("/home/user/Projects/app")
    }
}


