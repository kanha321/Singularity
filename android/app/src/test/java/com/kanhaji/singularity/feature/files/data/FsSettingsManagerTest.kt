package com.kanhaji.singularity.feature.files.data

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.kanhaji.singularity.feature.files.model.FsViewOptions
import com.kanhaji.singularity.feature.files.model.SortDirection
import com.kanhaji.singularity.feature.files.model.SortField
import com.kanhaji.singularity.testutil.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class FsSettingsManagerTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Before
    fun setUp() {
        FsSettingsManager.setOptions(FsViewOptions())
    }

    @Test
    fun updateOptions_modifySortBy_emitsUpdatedState() = runTest {
        FsSettingsManager.viewOptions.test {
            val initial = awaitItem()
            assertThat(initial.sortBy).isEqualTo(SortField.NAME)

            FsSettingsManager.updateOptions { it.copy(sortBy = SortField.SIZE) }

            val updated = awaitItem()
            assertThat(updated.sortBy).isEqualTo(SortField.SIZE)
        }
    }

    @Test
    fun setOptions_newConfiguration_replacesViewOptions() = runTest {
        val custom = FsViewOptions(
            sortBy = SortField.MODIFIED,
            direction = SortDirection.DESCENDING,
            showHidden = true,
            foldersFirst = false
        )

        FsSettingsManager.viewOptions.test {
            awaitItem()

            FsSettingsManager.setOptions(custom)

            val emitted = awaitItem()
            assertThat(emitted.sortBy).isEqualTo(SortField.MODIFIED)
            assertThat(emitted.direction).isEqualTo(SortDirection.DESCENDING)
            assertThat(emitted.showHidden).isTrue()
            assertThat(emitted.foldersFirst).isFalse()
        }
    }
}
