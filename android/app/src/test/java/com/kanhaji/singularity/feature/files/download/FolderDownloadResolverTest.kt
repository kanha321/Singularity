package com.kanhaji.singularity.feature.files.download

import com.kanhaji.singularity.feature.files.model.FsItem
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderDownloadResolverTest {

    @Test
    fun resolve_regularFiles_returnsDirectTargetsAndLabel() = runTest {
        val files = listOf(
            FsItem("test.txt", "/home/user/test.txt", is_dir = false, size = 100),
            FsItem("photo.png", "/home/user/photo.png", is_dir = false, size = 500)
        )

        val resolved = FolderDownloadResolver.resolve(files) { Result.success(emptyList()) }

        assertEquals(2, resolved.targets.size)
        assertEquals("test.txt + 1 items", resolved.rootLabel)
        assertEquals(600L, resolved.totalBytes)
        assertEquals("test.txt", resolved.targets[0].relativeLocalPath)
        assertEquals("/home/user/test.txt", resolved.targets[0].remotePath)
        assertEquals("photo.png", resolved.targets[1].relativeLocalPath)
    }

    @Test
    fun resolve_nestedDirectories_recursivelyFlattensStructureWithRelativePaths() = runTest {
        val rootFolder = FsItem("Projects", "/home/user/Projects", is_dir = true)

        val fakeFileSystem = mapOf(
            "/home/user/Projects" to listOf(
                FsItem("src", "/home/user/Projects/src", is_dir = true),
                FsItem("README.md", "/home/user/Projects/README.md", is_dir = false, size = 1200)
            ),
            "/home/user/Projects/src" to listOf(
                FsItem("main.rs", "/home/user/Projects/src/main.rs", is_dir = false, size = 450)
            )
        )

        val resolved = FolderDownloadResolver.resolve(listOf(rootFolder)) { path ->
            Result.success(fakeFileSystem[path] ?: emptyList())
        }

        assertEquals("Projects", resolved.rootLabel)
        assertEquals(1650L, resolved.totalBytes)
        assertEquals(2, resolved.targets.size)
        assertTrue(resolved.directories.contains("Projects"))
        assertTrue(resolved.directories.contains("Projects/src"))
        assertEquals("Projects/src/main.rs", resolved.targets[0].relativeLocalPath)
        assertEquals(450L, resolved.targets[0].size)
        assertEquals("Projects/README.md", resolved.targets[1].relativeLocalPath)
        assertEquals(1200L, resolved.targets[1].size)
    }

    @Test
    fun resolve_mixedFilesAndFolders_preservesHierarchy() = runTest {
        val items = listOf(
            FsItem("notes.txt", "/home/user/notes.txt", is_dir = false, size = 20),
            FsItem("Docs", "/home/user/Docs", is_dir = true)
        )

        val fakeFileSystem = mapOf(
            "/home/user/Docs" to listOf(
                FsItem("report.pdf", "/home/user/Docs/report.pdf", is_dir = false, size = 300)
            )
        )

        val resolved = FolderDownloadResolver.resolve(items) { path ->
            Result.success(fakeFileSystem[path] ?: emptyList())
        }

        assertEquals(2, resolved.targets.size)
        assertEquals("notes.txt", resolved.targets[0].relativeLocalPath)
        assertEquals("Docs/report.pdf", resolved.targets[1].relativeLocalPath)
        assertEquals(320L, resolved.totalBytes)
    }

    @Test
    fun resolve_withExclusions_filtersOutNoisyDirsAndTempFiles() = runTest {
        val rootFolder = FsItem("Project", "/home/user/Project", is_dir = true)

        val fakeFileSystem = mapOf(
            "/home/user/Project" to listOf(
                FsItem(".git", "/home/user/Project/.git", is_dir = true),
                FsItem("node_modules", "/home/user/Project/node_modules", is_dir = true),
                FsItem(".DS_Store", "/home/user/Project/.DS_Store", is_dir = false, size = 6000),
                FsItem("scratch.tmp", "/home/user/Project/scratch.tmp", is_dir = false, size = 100),
                FsItem("src", "/home/user/Project/src", is_dir = true),
                FsItem("package.json", "/home/user/Project/package.json", is_dir = false, size = 800)
            ),
            "/home/user/Project/src" to listOf(
                FsItem("index.js", "/home/user/Project/src/index.js", is_dir = false, size = 1500)
            )
        )

        val resolved = FolderDownloadResolver.resolve(listOf(rootFolder)) { path ->
            Result.success(fakeFileSystem[path] ?: emptyList())
        }

        assertEquals(2, resolved.targets.size)
        assertEquals("Project/src/index.js", resolved.targets[0].relativeLocalPath)
        assertEquals("Project/package.json", resolved.targets[1].relativeLocalPath)
    }
}
