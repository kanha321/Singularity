package com.kanhaji.singularity.feature.files.data

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.kanhaji.singularity.feature.files.model.FsItem
import com.kanhaji.singularity.testutil.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class FsSelectionManagerTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun dummy(name: String, isDir: Boolean = false) = FsItem(
        name = name,
        path = "/test/$name",
        is_dir = isDir
    )

    @Before
    fun setUp() {
        FsSelectionManager.clear()
        FsSelectionManager.setCurrentItems(emptyList())
    }

    @Test
    fun startSelection_singleItem_emitsActiveSelectionState() = runTest {
        val item = dummy("doc.pdf")

        FsSelectionManager.state.test {
            assertThat(awaitItem().isActive).isFalse()

            FsSelectionManager.startSelection(item)

            val updated = awaitItem()
            assertThat(updated.isActive).isTrue()
            assertThat(updated.count).isEqualTo(1)
            assertThat(updated.isSelected(item)).isTrue()
        }
    }

    @Test
    fun toggle_itemToggledTwice_addsThenRemovesItem() = runTest {
        val item = dummy("image.png")

        FsSelectionManager.state.test {
            assertThat(awaitItem().count).isEqualTo(0)

            FsSelectionManager.toggle(item)
            val added = awaitItem()
            assertThat(added.count).isEqualTo(1)

            FsSelectionManager.toggle(item)
            val removed = awaitItem()
            assertThat(removed.count).isEqualTo(0)
            assertThat(removed.isActive).isFalse()
        }
    }

    @Test
    fun selectAll_withCurrentItemsPopulated_selectsAllEntries() = runTest {
        val items = listOf(dummy("one.txt"), dummy("two.txt"), dummy("folder", isDir = true))
        FsSelectionManager.setCurrentItems(items)

        FsSelectionManager.state.test {
            assertThat(awaitItem().count).isEqualTo(0)

            FsSelectionManager.selectAll()
            val allSelected = awaitItem()

            assertThat(allSelected.count).isEqualTo(3)
            assertThat(allSelected.filesCount).isEqualTo(2)
            assertThat(allSelected.foldersCount).isEqualTo(1)
        }
    }

    @Test
    fun clear_whenItemsSelected_resetsStateToEmpty() = runTest {
        val item = dummy("data.bin")
        FsSelectionManager.startSelection(item)

        FsSelectionManager.state.test {
            assertThat(awaitItem().count).isEqualTo(1)

            FsSelectionManager.clear()
            val cleared = awaitItem()

            assertThat(cleared.count).isEqualTo(0)
            assertThat(cleared.isActive).isFalse()
        }
    }
}
