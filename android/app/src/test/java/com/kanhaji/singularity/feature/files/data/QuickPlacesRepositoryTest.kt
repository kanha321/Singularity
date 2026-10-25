package com.kanhaji.singularity.feature.files.data

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.kanhaji.singularity.feature.files.model.QuickPlace
import com.kanhaji.singularity.testutil.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class QuickPlacesRepositoryTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Before
    fun setUp() {
        QuickPlacesRepository.resetToDefaults()
    }

    @Test
    fun places_initialState_containsDefaultPlacesWithHomeAndRoot() = runTest {
        QuickPlacesRepository.places.test {
            val list = awaitItem()
            assertThat(list).isNotEmpty()

            val names = list.map { it.name }
            assertThat(names).containsAtLeast("Home", "Downloads", "Projects", "Root")
        }
    }

    @Test
    fun places_initialState_marksRootAsProtectedAndHomeAsSafe() = runTest {
        val root = QuickPlacesRepository.getPlaceByPath("/")
        assertThat(root).isNotNull()
        assertThat(root?.isProtected).isTrue()

        val home = QuickPlacesRepository.getPlaceByPath("~")
        assertThat(home).isNotNull()
        assertThat(home?.isProtected).isFalse()
    }

    @Test
    fun addBookmark_newDirectory_appendsCustomBookmark() = runTest {
        QuickPlacesRepository.places.test {
            awaitItem()

            QuickPlacesRepository.addBookmark("Arch Config", "/home/kanhaji/.config")

            val updated = awaitItem()
            val added = updated.find { it.path == "/home/kanhaji/.config" }

            assertThat(added).isNotNull()
            assertThat(added?.name).isEqualTo("Arch Config")
            assertThat(added?.isCustomBookmark).isTrue()
        }
    }

    @Test
    fun addBookmark_duplicatePath_doesNotAddDuplicate() = runTest {
        QuickPlacesRepository.addBookmark("My Projects", "~/Projects")

        val countBefore = QuickPlacesRepository.places.value.size
        QuickPlacesRepository.addBookmark("My Projects Duplicate", "~/Projects")
        val countAfter = QuickPlacesRepository.places.value.size

        assertThat(countAfter).isEqualTo(countBefore)
    }

    @Test
    fun removeBookmark_customBookmark_removesFromList() = runTest {
        QuickPlacesRepository.addBookmark("Notes", "~/Documents/notes")

        QuickPlacesRepository.places.test {
            awaitItem()

            val removed = QuickPlacesRepository.removeBookmark("~/Documents/notes")
            assertThat(removed).isTrue()

            val updated = awaitItem()
            assertThat(updated.any { it.path == "~/Documents/notes" }).isFalse()
        }
    }

    @Test
    fun removeBookmark_defaultPlace_cannotBeRemoved() = runTest {
        val removed = QuickPlacesRepository.removeBookmark("/")
        assertThat(removed).isFalse()

        assertThat(QuickPlacesRepository.getPlaceByPath("/")).isNotNull()
    }

    @Test
    fun isBookmarked_existingOrCustom_returnsCorrectStatus() {
        assertThat(QuickPlacesRepository.isBookmarked("~")).isTrue()
        assertThat(QuickPlacesRepository.isBookmarked("/")).isTrue()
        assertThat(QuickPlacesRepository.isBookmarked("/some/random/path")).isFalse()

        QuickPlacesRepository.addBookmark("Custom", "/some/random/path")
        assertThat(QuickPlacesRepository.isBookmarked("/some/random/path")).isTrue()
    }
}
