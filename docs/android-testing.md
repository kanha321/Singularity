# Singularity — Android Testing Guide

*Personalized testing guide for the Singularity Android Companion App*  
*Kotlin · Jetpack Compose (Material 3 Expressive) · Koin · Coroutines · StateFlow · Custom Navigation*

---

## 1. Architecture Context & Testing Philosophy

Unlike standard tutorial apps, **Singularity** is a hardware-convergence and remote management companion for Arch Linux. Its architecture is characterized by:

1. **Koin (Not Hilt):** Lightweight, pure-Kotlin dependency injection with zero annotation processors or code generation.
2. **Material 3 Expressive UI:** Built on `material3: 1.5.0-alpha08` (`HorizontalFloatingToolbar`, `VibrantFloatingActionButton`, tactile spring physics).
3. **Core Framework Navigation:** Lightweight in-house navigation (`ScreenNavDisplay`, `AppNavigator`, `NavScreen`), avoiding the heavyweight Jetpack Navigation Component.
4. **Persistent Daemon IPC & HTTP Server:** Local bridge connections over abstract UNIX domain sockets (`localabstract:singularity_app`) and chunked HTTP endpoints (`http://127.0.0.1:8920/api/fs/...`).

### The Golden Rule
> **Never run a test on an Android device or emulator if it can run on the Host JVM.**  
> Host JVM tests (`app/src/test/`) execute in **milliseconds**, are 100% deterministic, and require zero emulator overhead or connected phones.

---

## 2. Directory Structure & Test Placement

Following Android standards and Singularity's modular feature boundaries:

```text
android/app/
├── src/
│   ├── main/java/com/kanhaji/singularity/
│   │   ├── feature/
│   │   │   ├── files/          # Remote File Browser
│   │   │   ├── auth/           # Biometric & PAM Auth settings
│   │   │   ├── power/          # PC Power actions
│   │   │   └── home/           # Dashboard & Connection banner
│   │   ├── service/            # AppBridgeService (Daemon socket)
│   │   └── ui/components/      # SingularitySnackbarHost, etc.
│   │
│   ├── test/java/com/kanhaji/singularity/          # FAST HOST JVM TESTS (< 2s)
│   │   ├── feature/
│   │   │   ├── files/
│   │   │   │   ├── model/
│   │   │   │   │   ├── FsViewOptionsTest.kt
│   │   │   │   │   └── FsSelectionStateTest.kt
│   │   │   │   ├── util/
│   │   │   │   │   └── FsPathUtilsTest.kt
│   │   │   │   └── data/
│   │   │   │       ├── FsScrollStateManagerTest.kt
│   │   │   │       ├── FsSelectionManagerTest.kt
│   │   │   │       └── FileBrowserRepositoryTest.kt
│   │   │   ├── auth/
│   │   │   │   └── AuthSettingsManagerTest.kt
│   │   │   └── power/
│   │   │       └── PowerActionTest.kt
│   │   └── di/
│   │       └── KoinGraphVerificationTest.kt
│   │
│   └── androidTest/java/com/kanhaji/singularity/   # DEVICE / EMULATOR TESTS
│       └── feature/files/
│           └── FsListCardComposeTest.kt
```

---

## 3. Gradle Testing Stack (`libs.versions.toml`)

To implement this suite, we add only lightweight, focused testing libraries to `android/gradle/libs.versions.toml`:

```toml
[versions]
junit = "4.13.2"
truth = "1.4.4"
kotlinxCoroutines = "1.10.1"
turbine = "1.2.0"
koin = "4.2.2"
mockwebserver = "4.12.0"

[libraries]
# Runner & Assertions
junit = { group = "junit", name = "junit", version.ref = "junit" }
truth = { group = "com.google.truth", name = "truth", version.ref = "truth" }

# Coroutines & StateFlow
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "kotlinxCoroutines" }
turbine = { group = "app.cash.turbine", name = "turbine", version.ref = "turbine" }

# Koin Testing
koin-test = { group = "io.insert-koin", name = "koin-test", version.ref = "koin" }
koin-test-junit4 = { group = "io.insert-koin", name = "koin-test-junit4", version.ref = "koin" }

# Mocking HTTP daemon endpoints
mockwebserver = { group = "com.squareup.okhttp3", name = "mockwebserver", version.ref = "mockwebserver" }
```

In `android/app/build.gradle.kts`:
```kotlin
dependencies {
    // Local JVM Unit Tests
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.koin.test)
    testImplementation(libs.koin.test.junit4)
    testImplementation(libs.mockwebserver)

    // Instrumented Compose Tests
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
```

---

## 4. Test Naming & Structure Conventions

Every unit test must strictly follow the workspace naming convention specified in `docs/unit_testing_rules.md`:

```text
<function>_<condition>_<expectedResult>
```

Always adhere to the **Arrange, Act, Assert (AAA)** pattern:

```kotlin
@Test
fun applyOptions_showHiddenFalse_filtersOutDotfiles() {
    // 1. Arrange
    val items = listOf(
        createDummyItem(name = ".config", isHidden = true),
        createDummyItem(name = "Documents", isHidden = false)
    )
    val options = FsViewOptions(showHidden = false)

    // 2. Act
    val result = items.applyOptions(options)

    // 3. Assert
    assertThat(result).hasSize(1)
    assertThat(result.first().name).isEqualTo("Documents")
}
```

---

## 5. Implementation Guide by Layer

### Layer 1: Pure Logic & Immutable Reducers (Zero Dependencies)

Pure models have zero external dependencies. They test instantly.

#### Example: `FsSelectionStateTest.kt`
```kotlin
package com.kanhaji.singularity.feature.files.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FsSelectionStateTest {

    private fun dummyItem(path: String, size: Long = 1024L, isDir: Boolean = false) = FsItem(
        name = path.substringAfterLast('/'),
        path = path,
        is_dir = isDir,
        size = size,
        modified = 0L,
        mime_type = "text/plain",
        is_symlink = false,
        is_hidden = false
    )

    @Test
    fun toggle_itemNotSelected_addsItemToSelection() {
        val state = FsSelectionState()
        val item = dummyItem("/home/kanhaji/test.txt")

        val next = state.toggle(item)

        assertThat(next.count).isEqualTo(1)
        assertThat(next.isSelected(item)).isTrue()
    }

    @Test
    fun toggle_itemAlreadySelected_removesItemAndDeactivates() {
        val item = dummyItem("/home/kanhaji/test.txt")
        val state = FsSelectionState(setOf(item))

        val next = state.toggle(item)

        assertThat(next.count).isEqualTo(0)
        assertThat(next.isActive).isFalse()
    }

    @Test
    fun formattedTotalSize_gigabytesRange_formatsCorrectly() {
        val bigFile = dummyItem("/home/kanhaji/video.mkv", size = 2L * 1024 * 1024 * 1024)
        val state = FsSelectionState(setOf(bigFile))

        assertThat(state.formattedTotalSize).isEqualTo("2.0 GB")
    }
}
```

---

### Layer 2: Reactive State & Coroutines (Turbine & `runTest`)

Singularity orchestrates features through reactive singletons (`FsSelectionManager`, `FsSettingsManager`, `AuthSettingsManager`).

#### The Main Dispatcher Rule:
Because JVM tests do not have Android's `Looper.getMainLooper()`, create a reusable JUnit Rule:

```kotlin
// app/src/test/java/com/kanhaji/singularity/testutil/MainDispatcherRule.kt
package com.kanhaji.singularity.testutil

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val testDispatcher: TestDispatcher = StandardTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }
    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
```

#### Example: `FsSelectionManagerTest.kt`
```kotlin
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

    @Before
    fun setUp() {
        FsSelectionManager.clear()
    }

    @Test
    fun selectAll_withCurrentItems_updatesStateFlow() = runTest {
        val items = listOf(
            FsItem("a.txt", "/a.txt", false, 100, 0, "text/plain", false, false),
            FsItem("b.txt", "/b.txt", false, 200, 0, "text/plain", false, false)
        )
        FsSelectionManager.setCurrentItems(items)

        FsSelectionManager.state.test {
            assertThat(awaitItem().count).isEqualTo(0)

            FsSelectionManager.selectAll()

            val updated = awaitItem()
            assertThat(updated.count).isEqualTo(2)
            assertThat(updated.filesCount).isEqualTo(2)
        }
    }
}
```

---

### Layer 3: Dependency Injection Testing with Koin (`koin-test`)

Instead of Hilt's annotation-heavy runners, Koin tests execute as plain JUnit classes implementing `KoinTest`:

```kotlin
package com.kanhaji.singularity.di

import org.junit.After
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.get

class KoinModuleTest : KoinTest {

    private val testModule = module {
        // Provide mock/fake singletons
    }

    @Before
    fun setUp() {
        startKoin { modules(testModule) }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun injectDependency_resolvesSuccessfully() {
        // val repo = get<FileBrowserRepository>()
        // assertThat(repo).isNotNull()
    }
}
```

---

### Layer 4: Network & HTTP Daemon Testing (`MockWebServer`)

`FileBrowserRepository` communicates with the laptop daemon over HTTP (`/api/fs/list`). We test HTTP parsing, JSON mapping, and failure fallbacks without requiring a live laptop daemon:

```kotlin
package com.kanhaji.singularity.feature.files.data

import com.google.common.truth.Truth.assertThat
import com.kanhaji.singularity.service.AppBridgeService
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class FileBrowserRepositoryTest {

    private lateinit var mockServer: MockWebServer

    @Before
    fun setUp() {
        mockServer = MockWebServer()
        mockServer.start()
        AppBridgeService.fileServerUrl.value = mockServer.url("/").toString().removeSuffix("/")
    }

    @After
    fun tearDown() {
        mockServer.shutdown()
    }

    @Test
    fun listDirectory_validJson_returnsParsedEntries() = runTest {
        val json = """
        [
            {"name":"Projects","path":"/home/kanhaji/Projects","is_dir":true,"size":4096,"modified":1700000000,"mime_type":"inode/directory","is_symlink":false,"is_hidden":false}
        ]
        """.trimIndent()
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody(json))

        val result = FileBrowserRepository.listDirectory("/home/kanhaji")

        assertThat(result.isSuccess).isTrue()
        val items = result.getOrThrow()
        assertThat(items).hasSize(1)
        assertThat(items.first().name).isEqualTo("Projects")
        assertThat(items.first().is_dir).isTrue()
    }

    @Test
    fun listDirectory_serverError_returnsFailure() = runTest {
        mockServer.enqueue(MockResponse().setResponseCode(500).setBody("Internal Server Error"))

        val result = FileBrowserRepository.listDirectory("/root")

        assertThat(result.isFailure).isTrue()
    }
}
```

---

### Layer 5: Compose UI Testing (`androidTest`)

When testing UI semantics, state toggling, and Material 3 Expressive elements:

```kotlin
package com.kanhaji.singularity.feature.files.components

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.kanhaji.singularity.feature.files.model.FsItem
import org.junit.Rule
import org.junit.Test

class FsListCardComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun fsListCard_displaysItemNameAndSubtitle() {
        val item = FsItem(
            name = "report.pdf",
            path = "/docs/report.pdf",
            is_dir = false,
            size = 2048,
            modified = 1700000000,
            mime_type = "application/pdf",
            is_symlink = false,
            is_hidden = false
        )

        composeTestRule.setContent {
            FsListCard(
                item = item,
                onClick = {},
                onLongClick = {}
            )
        }

        composeTestRule.onNodeWithText("report.pdf").assertIsDisplayed()
        composeTestRule.onNodeWithText("2.0 KB", substring = true).assertIsDisplayed()
    }
}
```

---

## 6. How to Execute Tests

Execute all JVM tests from the workspace root:

```bash
# Run all local JVM unit tests
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ANDROID_HOME=/home/kanhaji/Android/Sdk \
  ./android/gradlew -p android testDebugUnitTest

# Run a specific test class
./android/gradlew -p android testDebugUnitTest --tests "com.kanhaji.singularity.feature.files.model.FsViewOptionsTest"

# Run with continuous test watching during development
./android/gradlew -p android testDebugUnitTest --continuous
```

---

## 7. Priority Testing Checklist for Singularity

- [ ] **`FsViewOptionsTest`**: Verify sorting (Name, Date, Size, Type) and hidden dotfile filtering.
- [ ] **`FsSelectionStateTest`**: Verify selection toggles, count invariants, and formatted byte sizes.
- [ ] **`FsPathUtilsTest`**: Verify breadcrumb path formatting (`~`, `/`, empty strings).
- [ ] **`FsScrollStateManagerTest`**: Verify scroll coordinate retrieval across paths.
- [ ] **`FsSelectionManagerTest`**: Verify reactive StateFlow emissions using Turbine.
- [ ] **`FileBrowserRepositoryTest`**: Verify HTTP JSON mapping using MockWebServer.
- [ ] **`PowerActionTest`**: Verify command payload definitions and confirmation requirements.
- [ ] **`AuthSettingsManagerTest`**: Verify PAM/Biometric auth modes and timeout intervals.
