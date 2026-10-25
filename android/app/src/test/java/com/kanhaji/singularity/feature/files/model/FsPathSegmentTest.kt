package com.kanhaji.singularity.feature.files.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FsPathSegmentTest {

    @Test
    fun parsePathSegments_emptyOrTilde_returnsSingleHomeSegment() {
        val emptySegments = parsePathSegments("")
        assertThat(emptySegments).hasSize(1)
        assertThat(emptySegments.first().name).isEqualTo("Home")
        assertThat(emptySegments.first().fullPath).isEqualTo("~")
        assertThat(emptySegments.first().isHome).isTrue()

        val tildeSegments = parsePathSegments("~")
        assertThat(tildeSegments).hasSize(1)
        assertThat(tildeSegments.first().name).isEqualTo("Home")
    }

    @Test
    fun parsePathSegments_tildeSubdirectory_returnsHomePlusNestedSegments() {
        val segments = parsePathSegments("~/Projects/singularity")

        assertThat(segments).hasSize(3)
        assertThat(segments[0].name).isEqualTo("Home")
        assertThat(segments[0].fullPath).isEqualTo("~")
        assertThat(segments[0].isHome).isTrue()

        assertThat(segments[1].name).isEqualTo("Projects")
        assertThat(segments[1].fullPath).isEqualTo("~/Projects")

        assertThat(segments[2].name).isEqualTo("singularity")
        assertThat(segments[2].fullPath).isEqualTo("~/Projects/singularity")
    }

    @Test
    fun parsePathSegments_homeKanhajiPrefix_resolvesHomeAliasWithSubdirs() {
        val segments = parsePathSegments("/home/kanhaji/Documents/work")

        assertThat(segments).hasSize(3)
        assertThat(segments[0].name).isEqualTo("Home")
        assertThat(segments[0].isHome).isTrue()

        assertThat(segments[1].name).isEqualTo("Documents")
        assertThat(segments[1].fullPath).isEqualTo("/home/kanhaji/Documents")

        assertThat(segments[2].name).isEqualTo("work")
        assertThat(segments[2].fullPath).isEqualTo("/home/kanhaji/Documents/work")
    }

    @Test
    fun parsePathSegments_rootSlash_returnsSingleRootSegment() {
        val segments = parsePathSegments("/")

        assertThat(segments).hasSize(1)
        assertThat(segments.first().name).isEqualTo("/")
        assertThat(segments.first().fullPath).isEqualTo("/")
        assertThat(segments.first().isRoot).isTrue()
    }

    @Test
    fun parsePathSegments_systemPath_returnsRootPlusSegments() {
        val segments = parsePathSegments("/etc/systemd")

        assertThat(segments).hasSize(3)
        assertThat(segments[0].name).isEqualTo("/")
        assertThat(segments[0].isRoot).isTrue()

        assertThat(segments[1].name).isEqualTo("etc")
        assertThat(segments[1].fullPath).isEqualTo("/etc")

        assertThat(segments[2].name).isEqualTo("systemd")
        assertThat(segments[2].fullPath).isEqualTo("/etc/systemd")
    }

    @Test
    fun parsePathSegments_sudoActiveAtHome_prependsRootSlashSegment() {
        val segments = parsePathSegments("~", isSudoActive = true)

        assertThat(segments).hasSize(2)
        assertThat(segments[0].name).isEqualTo("/")
        assertThat(segments[0].fullPath).isEqualTo("/")
        assertThat(segments[0].isRoot).isTrue()

        assertThat(segments[1].name).isEqualTo("Home")
        assertThat(segments[1].fullPath).isEqualTo("~")
        assertThat(segments[1].isHome).isTrue()
    }

    @Test
    fun parsePathSegments_sudoActiveAtSubdirectory_prependsRootSlashSegment() {
        val segments = parsePathSegments("~/Downloads", isSudoActive = true)

        assertThat(segments).hasSize(3)
        assertThat(segments[0].name).isEqualTo("/")
        assertThat(segments[0].fullPath).isEqualTo("/")
        assertThat(segments[0].isRoot).isTrue()

        assertThat(segments[1].name).isEqualTo("Home")
        assertThat(segments[2].name).isEqualTo("Downloads")
    }

    @Test
    fun parsePathSegments_withArchiveRoot_returnsSegmentsWithArchiveFlag() {
        val segments = parsePathSegments("~/Projects/code.zip!/")
        assertThat(segments).hasSize(3)
        assertThat(segments[0].name).isEqualTo("Home")
        assertThat(segments[1].name).isEqualTo("Projects")
        assertThat(segments[2].name).isEqualTo("code.zip")
        assertThat(segments[2].fullPath).isEqualTo("~/Projects/code.zip!/")
        assertThat(segments[2].isArchive).isTrue()
    }

    @Test
    fun parsePathSegments_withArchiveSubdirectory_returnsNestedArchiveSegments() {
        val segments = parsePathSegments("~/Projects/code.zip!/src/main")
        assertThat(segments).hasSize(5)
        assertThat(segments[2].name).isEqualTo("code.zip")
        assertThat(segments[2].isArchive).isTrue()
        assertThat(segments[3].name).isEqualTo("src")
        assertThat(segments[3].fullPath).isEqualTo("~/Projects/code.zip!/src")
        assertThat(segments[4].name).isEqualTo("main")
        assertThat(segments[4].fullPath).isEqualTo("~/Projects/code.zip!/src/main")
    }
}
