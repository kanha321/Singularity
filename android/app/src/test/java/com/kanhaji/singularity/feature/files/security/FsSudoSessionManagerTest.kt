package com.kanhaji.singularity.feature.files.security

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.kanhaji.singularity.testutil.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class FsSudoSessionManagerTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Before
    fun setUp() {
        FsSudoSessionManager.dropPrivileges()
    }

    @Test
    fun elevate_whenCalled_transitionsToElevatedState() = runTest {
        FsSudoSessionManager.state.test {
            val initial = awaitItem()
            assertThat(initial.isElevated).isFalse()

            FsSudoSessionManager.elevate()

            val elevated = awaitItem()
            assertThat(elevated.isElevated).isTrue()
        }
    }

    @Test
    fun dropPrivileges_whenElevated_resetsToNormalState() = runTest {
        FsSudoSessionManager.elevate()

        FsSudoSessionManager.state.test {
            val initial = awaitItem()
            assertThat(initial.isElevated).isTrue()

            FsSudoSessionManager.dropPrivileges()

            val dropped = awaitItem()
            assertThat(dropped.isElevated).isFalse()
        }
    }

    @Test
    fun canAccessPath_rootPathWhenNotElevated_returnsFalse() {
        assertThat(FsSudoSessionManager.canAccessPath("/")).isFalse()
        assertThat(FsSudoSessionManager.canAccessPath("/etc/systemd")).isFalse()
        assertThat(FsSudoSessionManager.canAccessPath("/var/log")).isFalse()
    }

    @Test
    fun canAccessPath_rootPathWhenElevated_returnsTrue() {
        FsSudoSessionManager.elevate()

        assertThat(FsSudoSessionManager.canAccessPath("/")).isTrue()
        assertThat(FsSudoSessionManager.canAccessPath("/etc/systemd")).isTrue()
        assertThat(FsSudoSessionManager.canAccessPath("/var/log")).isTrue()
    }

    @Test
    fun canAccessPath_userHomePathWhenNotElevated_alwaysReturnsTrue() {
        assertThat(FsSudoSessionManager.canAccessPath("~")).isTrue()
        assertThat(FsSudoSessionManager.canAccessPath("~/Downloads")).isTrue()
        assertThat(FsSudoSessionManager.canAccessPath("/home/kanhaji/Projects")).isTrue()
    }
}
