package com.kanhaji.singularity.feature.files.data

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

class FsScrollStateManagerTest {

    @Before
    fun setUp() {
        FsScrollStateManager.clear()
    }

    @Test
    fun savePosition_validPath_storesAndRetrievesCoordinates() {
        FsScrollStateManager.savePosition("/home/kanhaji/Projects", index = 14, offset = 42)

        val pos = FsScrollStateManager.getPosition("/home/kanhaji/Projects")

        assertThat(pos).isNotNull()
        assertThat(pos?.first).isEqualTo(14)
        assertThat(pos?.second).isEqualTo(42)
    }

    @Test
    fun savePosition_emptyPath_doesNotStoreCoordinates() {
        FsScrollStateManager.savePosition("", index = 5, offset = 10)

        val pos = FsScrollStateManager.getPosition("")

        assertThat(pos).isNull()
    }

    @Test
    fun clear_withStoredCoordinates_resetsAllEntries() {
        FsScrollStateManager.savePosition("/path/a", 2, 4)
        FsScrollStateManager.savePosition("/path/b", 10, 20)

        FsScrollStateManager.clear()

        assertThat(FsScrollStateManager.getPosition("/path/a")).isNull()
        assertThat(FsScrollStateManager.getPosition("/path/b")).isNull()
    }
}
