package com.kanhaji.singularity.feature.files.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FsViewOptionsTest {

    private fun createItem(
        name: String,
        isDir: Boolean = false,
        size: Long = 100L,
        modified: Long = 1000L,
        mimeType: String = "text/plain",
        isHidden: Boolean = false
    ) = FsItem(
        name = name,
        path = "/test/$name",
        is_dir = isDir,
        size = size,
        modified = modified,
        mime_type = mimeType,
        is_hidden = isHidden
    )

    @Test
    fun applyOptions_showHiddenFalse_filtersOutHiddenItems() {
        val items = listOf(
            createItem(".config", isDir = true, isHidden = true),
            createItem("Documents", isDir = true, isHidden = false)
        )

        val result = items.applyOptions(FsViewOptions(showHidden = false))

        assertThat(result).hasSize(1)
        assertThat(result.first().name).isEqualTo("Documents")
    }

    @Test
    fun applyOptions_showHiddenTrue_retainsHiddenItems() {
        val items = listOf(
            createItem(".config", isDir = true, isHidden = true),
            createItem("Documents", isDir = true, isHidden = false)
        )

        val result = items.applyOptions(FsViewOptions(showHidden = true))

        assertThat(result).hasSize(2)
    }

    @Test
    fun applyOptions_foldersFirstTrue_placesFoldersBeforeFiles() {
        val items = listOf(
            createItem("a_file.txt", isDir = false),
            createItem("z_folder", isDir = true)
        )

        val result = items.applyOptions(FsViewOptions(foldersFirst = true, sortBy = SortField.NAME))

        assertThat(result.first().name).isEqualTo("z_folder")
        assertThat(result.last().name).isEqualTo("a_file.txt")
    }

    @Test
    fun applyOptions_sortByNameDescending_ordersAlphabeticallyReversed() {
        val items = listOf(
            createItem("Alpha"),
            createItem("Beta"),
            createItem("Gamma")
        )

        val result = items.applyOptions(
            FsViewOptions(sortBy = SortField.NAME, direction = SortDirection.DESCENDING)
        )

        assertThat(result.map { it.name }).containsExactly("Gamma", "Beta", "Alpha").inOrder()
    }

    @Test
    fun applyOptions_sortBySizeAscending_ordersSmallestToLargest() {
        val items = listOf(
            createItem("large.bin", size = 5000L),
            createItem("small.bin", size = 50L),
            createItem("medium.bin", size = 500L)
        )

        val result = items.applyOptions(
            FsViewOptions(sortBy = SortField.SIZE, direction = SortDirection.ASCENDING)
        )

        assertThat(result.map { it.name }).containsExactly("small.bin", "medium.bin", "large.bin").inOrder()
    }

    @Test
    fun applyOptions_sortByModifiedDescending_ordersNewestFirst() {
        val items = listOf(
            createItem("old.txt", modified = 100L),
            createItem("newest.txt", modified = 900L),
            createItem("middle.txt", modified = 500L)
        )

        val result = items.applyOptions(
            FsViewOptions(sortBy = SortField.MODIFIED, direction = SortDirection.DESCENDING)
        )

        assertThat(result.map { it.name }).containsExactly("newest.txt", "middle.txt", "old.txt").inOrder()
    }
}
