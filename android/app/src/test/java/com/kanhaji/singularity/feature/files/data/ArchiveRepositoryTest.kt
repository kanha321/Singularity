package com.kanhaji.singularity.feature.files.data

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

class ArchiveRepositoryTest {

    @Before
    fun setUp() {
        ArchiveRepository.clearCache()
    }

    @Test
    fun filterEntries_rootDirectory_returnsDirectChildrenAndSynthesizedFolders() {
        val rawEntries = listOf(
            RawArchiveEntry("README.md", is_dir = false, size = 100, modified = 0L),
            RawArchiveEntry("src/main.rs", is_dir = false, size = 500, modified = 0L),
            RawArchiveEntry("docs/plan.md", is_dir = false, size = 300, modified = 0L)
        )

        val items = ArchiveRepository.filterEntries(
            archivePath = "/home/user/project.zip",
            allEntries = rawEntries,
            internalDir = ""
        )

        assertThat(items).hasSize(3)
        val names = items.map { it.name }
        assertThat(names).containsExactly("README.md", "src", "docs")

        val srcItem = items.first { it.name == "src" }
        assertThat(srcItem.is_dir).isTrue()
        assertThat(srcItem.path).isEqualTo("/home/user/project.zip!/src")

        val readmeItem = items.first { it.name == "README.md" }
        assertThat(readmeItem.is_dir).isFalse()
        assertThat(readmeItem.path).isEqualTo("/home/user/project.zip!/README.md")
    }

    @Test
    fun filterEntries_nestedDirectory_returnsOnlyEntriesWithinSubdirectory() {
        val rawEntries = listOf(
            RawArchiveEntry("README.md", is_dir = false, size = 100, modified = 0L),
            RawArchiveEntry("src/main.rs", is_dir = false, size = 500, modified = 0L),
            RawArchiveEntry("src/utils/math.rs", is_dir = false, size = 200, modified = 0L)
        )

        val items = ArchiveRepository.filterEntries(
            archivePath = "/home/user/project.zip",
            allEntries = rawEntries,
            internalDir = "src"
        )

        assertThat(items).hasSize(2)
        val names = items.map { it.name }
        assertThat(names).containsExactly("main.rs", "utils")

        val utilsItem = items.first { it.name == "utils" }
        assertThat(utilsItem.is_dir).isTrue()
        assertThat(utilsItem.path).isEqualTo("/home/user/project.zip!/src/utils")
    }
}
