package com.kanhaji.singularity.feature.files.data

import com.kanhaji.singularity.feature.files.model.FsItem
import org.junit.Assert.*
import org.junit.Test

class FsDialogManagerTest {

    private fun testItem(name: String) = FsItem(
        name = name,
        path = "/home/$name",
        is_dir = false,
        size = 10,
        modified = 0,
        mime_type = "text/plain",
        is_symlink = false,
        is_hidden = false
    )

    @Test
    fun dialogManager_opensAndClosesStatesProperly() {
        val item = testItem("test.txt")

        FsDialogManager.openContextMenu(item)
        assertEquals(item, FsDialogManager.contextMenuItem.value)
        FsDialogManager.closeContextMenu()
        assertNull(FsDialogManager.contextMenuItem.value)

        FsDialogManager.openRename(item)
        assertEquals(item, FsDialogManager.renameItem.value)
        FsDialogManager.closeRename()
        assertNull(FsDialogManager.renameItem.value)

        FsDialogManager.openDelete(listOf(item))
        assertEquals(1, FsDialogManager.deleteItems.value?.size)
        FsDialogManager.closeDelete()
        assertNull(FsDialogManager.deleteItems.value)

        FsDialogManager.openNewFolder()
        assertTrue(FsDialogManager.showNewFolder.value)
        FsDialogManager.closeNewFolder()
        assertFalse(FsDialogManager.showNewFolder.value)
    }
}
