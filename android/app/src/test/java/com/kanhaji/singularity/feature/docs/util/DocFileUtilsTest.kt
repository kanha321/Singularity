package com.kanhaji.singularity.feature.docs.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocFileUtilsTest {

    @Test
    fun isTextFile_variousCodeAndConfigExtensions_returnsTrue() {
        assertTrue(DocFileUtils.isTextFile("notes.txt"))
        assertTrue(DocFileUtils.isTextFile("README.md"))
        assertTrue(DocFileUtils.isTextFile("Cargo.toml"))
        assertTrue(DocFileUtils.isTextFile("server.rs"))
        assertTrue(DocFileUtils.isTextFile("Main.kt"))
        assertTrue(DocFileUtils.isTextFile("script.py"))
        assertTrue(DocFileUtils.isTextFile("deploy.sh"))
        assertTrue(DocFileUtils.isTextFile("package.json"))
        assertTrue(DocFileUtils.isTextFile("app.log"))
        assertTrue(DocFileUtils.isTextFile("query.sql"))
    }

    @Test
    fun isTextFile_mediaAndBinaryExtensions_returnsFalse() {
        assertFalse(DocFileUtils.isTextFile("video.mp4"))
        assertFalse(DocFileUtils.isTextFile("image.png"))
        assertFalse(DocFileUtils.isTextFile("audio.mp3"))
        assertFalse(DocFileUtils.isTextFile("archive.zip"))
        assertFalse(DocFileUtils.isTextFile("document.pdf"))
    }

    @Test
    fun isPdfOrOffice_documentExtensions_returnsTrue() {
        assertTrue(DocFileUtils.isPdfOrOffice("manual.pdf"))
        assertTrue(DocFileUtils.isPdfOrOffice("report.docx"))
        assertTrue(DocFileUtils.isPdfOrOffice("budget.xlsx"))
        assertTrue(DocFileUtils.isPdfOrOffice("slides.pptx"))
        assertTrue(DocFileUtils.isPdfOrOffice("book.epub"))
    }

    @Test
    fun isPdfOrOffice_otherExtensions_returnsFalse() {
        assertFalse(DocFileUtils.isPdfOrOffice("notes.txt"))
        assertFalse(DocFileUtils.isPdfOrOffice("image.jpg"))
        assertFalse(DocFileUtils.isPdfOrOffice("video.mkv"))
    }

    @Test
    fun getMimeType_knownTypes_returnsExpectedMime() {
        assertEquals("application/pdf", DocFileUtils.getMimeType("document.pdf"))
        assertEquals("application/epub+zip", DocFileUtils.getMimeType("book.epub"))
        assertEquals("text/plain", DocFileUtils.getMimeType("notes.txt"))
        assertEquals("text/plain", DocFileUtils.getMimeType("server.rs"))
    }
}
