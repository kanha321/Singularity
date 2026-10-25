package com.kanhaji.singularity.feature.files.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FsSelectionStateTest {

    private fun dummyItem(path: String, size: Long = 1024L, isDir: Boolean = false) = FsItem(
        name = path.substringAfterLast('/'),
        path = path,
        is_dir = isDir,
        size = size
    )

    @Test
    fun toggle_itemNotSelected_addsItemAndActivates() {
        val state = FsSelectionState()
        val item = dummyItem("/home/kanhaji/notes.txt")

        val next = state.toggle(item)

        assertThat(next.count).isEqualTo(1)
        assertThat(next.isActive).isTrue()
        assertThat(next.isSelected(item)).isTrue()
    }

    @Test
    fun toggle_itemAlreadySelected_removesItemAndDeactivates() {
        val item = dummyItem("/home/kanhaji/notes.txt")
        val state = FsSelectionState(setOf(item))

        val next = state.toggle(item)

        assertThat(next.count).isEqualTo(0)
        assertThat(next.isActive).isFalse()
        assertThat(next.isSelected(item)).isFalse()
    }

    @Test
    fun selectAll_givenItemList_populatesAllItems() {
        val item1 = dummyItem("/home/kanhaji/a.txt", isDir = false)
        val item2 = dummyItem("/home/kanhaji/b", isDir = true)
        val state = FsSelectionState()

        val next = state.selectAll(listOf(item1, item2))

        assertThat(next.count).isEqualTo(2)
        assertThat(next.filesCount).isEqualTo(1)
        assertThat(next.foldersCount).isEqualTo(1)
    }

    @Test
    fun clear_withExistingSelection_resetsToEmpty() {
        val item = dummyItem("/home/kanhaji/doc.pdf")
        val state = FsSelectionState(setOf(item))

        val next = state.clear()

        assertThat(next.count).isEqualTo(0)
        assertThat(next.isActive).isFalse()
        assertThat(next.selectedItems).isEmpty()
    }

    @Test
    fun formattedTotalSize_variousRanges_formatsAppropriateUnits() {
        val bytesFile = dummyItem("/bytes.bin", size = 512L)
        val kbFile = dummyItem("/kb.bin", size = 2048L)
        val mbFile = dummyItem("/mb.bin", size = 5L * 1024 * 1024)
        val gbFile = dummyItem("/gb.bin", size = 3L * 1024 * 1024 * 1024)

        assertThat(FsSelectionState(setOf(bytesFile)).formattedTotalSize).isEqualTo("512 B")
        assertThat(FsSelectionState(setOf(kbFile)).formattedTotalSize).isEqualTo("2.0 KB")
        assertThat(FsSelectionState(setOf(mbFile)).formattedTotalSize).isEqualTo("5.0 MB")
        assertThat(FsSelectionState(setOf(gbFile)).formattedTotalSize).isEqualTo("3.0 GB")
    }
}
