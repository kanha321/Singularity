package com.kanhaji.singularity.feature.files.data

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.kanhaji.singularity.service.AppBridgeService
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress

class FileBrowserRepositoryTest {

    private lateinit var server: HttpServer
    private lateinit var originalUrl: String

    @Before
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.start()
        val port = server.address.port
        originalUrl = AppBridgeService.fileServerUrl.value
        AppBridgeService.fileServerUrl.value = "http://127.0.0.1:$port"
    }

    @After
    fun tearDown() {
        server.stop(0)
        AppBridgeService.fileServerUrl.value = originalUrl
    }

    @Test
    fun listDirectory_validJson200_parsesItemsAndCachesResult() = runTest {
        val json = """
        [
            {
                "name": "Projects",
                "path": "/home/kanhaji/Projects",
                "is_dir": true,
                "size": 4096,
                "modified": 1700000000,
                "mime_type": "inode/directory",
                "is_symlink": false,
                "is_hidden": false
            },
            {
                "name": "notes.txt",
                "path": "/home/kanhaji/notes.txt",
                "is_dir": false,
                "size": 128,
                "modified": 1700000100,
                "mime_type": "text/plain",
                "is_symlink": false,
                "is_hidden": false
            }
        ]
        """.trimIndent()

        server.createContext("/api/fs/list") { exchange ->
            val bytes = json.toByteArray()
            exchange.responseHeaders.set("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }

        val result = FileBrowserRepository.listDirectory("/home/kanhaji")

        assertThat(result.isSuccess).isTrue()
        val items = result.getOrThrow()
        assertThat(items).hasSize(2)

        val dir = items[0]
        assertThat(dir.name).isEqualTo("Projects")
        assertThat(dir.is_dir).isTrue()

        val file = items[1]
        assertThat(file.name).isEqualTo("notes.txt")
        assertThat(file.size).isEqualTo(128L)

        // Verify cache hit
        val cached = FileBrowserRepository.getCached("/home/kanhaji")
        assertThat(cached).isNotNull()
        assertThat(cached).hasSize(2)
    }

    @Test
    fun listDirectory_serverError500_returnsFailure() = runTest {
        server.createContext("/api/fs/list") { exchange ->
            val bytes = "Internal Server Error".toByteArray()
            exchange.sendResponseHeaders(500, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }

        val result = FileBrowserRepository.listDirectory("/nonexistent")

        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun streamFolderSize_validStreamingChunks_emitsProgressiveEvents() = runTest {
        val chunk1 = """{"bytes":1024,"files":10,"folders":2,"done":false}""" + "\n"
        val chunk2 = """{"bytes":40960,"files":120,"folders":8,"done":true}""" + "\n"

        server.createContext("/api/fs/folder_size") { exchange ->
            exchange.responseHeaders.set("Content-Type", "application/x-ndjson")
            exchange.sendResponseHeaders(200, 0)
            exchange.responseBody.bufferedWriter().use { writer ->
                writer.write(chunk1)
                writer.flush()
                writer.write(chunk2)
                writer.flush()
            }
        }

        FileBrowserRepository.streamFolderSize("/test/dir").test {
            val first = awaitItem()
            assertThat(first.bytes).isEqualTo(1024L)
            assertThat(first.filesCount).isEqualTo(10L)
            assertThat(first.isDone).isFalse()

            val second = awaitItem()
            assertThat(second.bytes).isEqualTo(40960L)
            assertThat(second.filesCount).isEqualTo(120L)
            assertThat(second.isDone).isTrue()

            awaitComplete()
        }
    }
}
