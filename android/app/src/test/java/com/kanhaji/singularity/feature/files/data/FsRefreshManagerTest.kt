package com.kanhaji.singularity.feature.files.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FsRefreshManagerTest {

    @Test
    fun triggerRefresh_incrementsTriggerValue() {
        val initial = FsRefreshManager.refreshTrigger.value
        FsRefreshManager.triggerRefresh()
        assertEquals(initial + 1, FsRefreshManager.refreshTrigger.value)
    }
}
