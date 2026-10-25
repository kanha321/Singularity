package com.kanhaji.singularity.feature.files.data

import com.kanhaji.singularity.feature.files.model.FsClipboardOp
import com.kanhaji.singularity.feature.files.model.FsItem
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class FsClipboardManagerTest {

    private fun testItem(name: String) = FsItem(
        name = name,
        path = "/home/kanhaji/$name",
        is_dir = false,
        size = 100,
        modified = 1000,
        mime_type = "text/plain",
        is_symlink = false,
        is_hidden = false
    )

    @After
    fun tearDown() {
        FsClipboardManager.clear()
    }

    @Test
    fun copy_withItems_setsCopyState() {
        val items = listOf(testItem("a.txt"), testItem("b.txt"))
        FsClipboardManager.copy(items)

        val state = FsClipboardManager.state.value
        assertTrue(state.isActive)
        assertEquals(2, state.count)
        assertEquals(FsClipboardOp.COPY, state.op)
    }

    @Test
    fun cut_withItems_setsCutState() {
        val items = listOf(testItem("c.txt"))
        FsClipboardManager.cut(items)

        val state = FsClipboardManager.state.value
        assertTrue(state.isActive)
        assertEquals(1, state.count)
        assertEquals(FsClipboardOp.CUT, state.op)
    }

    @Test
    fun clear_resetsStateToEmpty() {
        FsClipboardManager.copy(listOf(testItem("a.txt")))
        assertTrue(FsClipboardManager.state.value.isActive)

        FsClipboardManager.clear()
        assertFalse(FsClipboardManager.state.value.isActive)
        assertEquals(0, FsClipboardManager.state.value.count)
        assertNull(FsClipboardManager.state.value.op)
    }
}
