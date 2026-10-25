package com.kanhaji.singularity.feature.player.state

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.kanhaji.singularity.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerSwipeSeekStateTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun onDragStart_initializesStateWithZeroDelta() = runTest {
        val state = PlayerSwipeSeekState(scope = this)
        state.onDragStart(currentPosMs = 30_000L, durationMs = 120_000L)

        val uiState = state.uiState.value
        assertThat(uiState).isNotNull()
        assertThat(uiState?.deltaMs).isEqualTo(0L)
        assertThat(uiState?.targetPositionMs).isEqualTo(30_000L)
        assertThat(uiState?.deltaFormatted).isEqualTo("[+00:00]")
        assertThat(state.isSeeking).isTrue()
    }

    @Test
    fun onDragDelta_positive_computesForwardTargetAndFormattedDelta() = runTest {
        val state = PlayerSwipeSeekState(scope = this)
        state.onDragStart(currentPosMs = 30_000L, durationMs = 300_000L)

        // dragRatio = 0.5f -> delta = 60,000L (1 min)
        state.onDragDelta(0.5f)

        val uiState = state.uiState.value
        assertThat(uiState?.deltaMs).isEqualTo(60_000L)
        assertThat(uiState?.targetPositionMs).isEqualTo(90_000L)
        assertThat(uiState?.deltaFormatted).isEqualTo("[+01:00]")
    }

    @Test
    fun onDragDelta_negative_computesRewindTargetAndFormattedDelta() = runTest {
        val state = PlayerSwipeSeekState(scope = this)
        state.onDragStart(currentPosMs = 90_000L, durationMs = 300_000L)

        // dragRatio = -0.25f -> delta = -30,000L (-30s)
        state.onDragDelta(-0.25f)

        val uiState = state.uiState.value
        assertThat(uiState?.deltaMs).isEqualTo(-30_000L)
        assertThat(uiState?.targetPositionMs).isEqualTo(60_000L)
        assertThat(uiState?.deltaFormatted).isEqualTo("[-00:30]")
    }

    @Test
    fun onDragDelta_clampsToZeroAndTotalDuration() = runTest {
        val state = PlayerSwipeSeekState(scope = this)
        state.onDragStart(currentPosMs = 10_000L, durationMs = 100_000L)

        // Try rewinding beyond 0
        state.onDragDelta(-1.0f)
        assertThat(state.uiState.value?.targetPositionMs).isEqualTo(0L)
        assertThat(state.uiState.value?.deltaMs).isEqualTo(-10_000L)

        // Try fast-forwarding beyond duration
        state.onDragDelta(2.0f)
        assertThat(state.uiState.value?.targetPositionMs).isEqualTo(100_000L)
        assertThat(state.uiState.value?.deltaMs).isEqualTo(90_000L)
    }

    @Test
    fun onDragEnd_commitsTargetPositionAndDismissesAfterTimeout() = runTest {
        var committedPosition = -1L
        val state = PlayerSwipeSeekState(
            scope = this,
            dismissTimeoutMillis = 500L,
            onSeekCommit = { committedPosition = it }
        )

        state.uiState.test {
            assertThat(awaitItem()).isNull()

            state.onDragStart(currentPosMs = 20_000L, durationMs = 100_000L)
            awaitItem()

            state.onDragDelta(0.25f)
            val updated = awaitItem()
            assertThat(updated?.targetPositionMs).isEqualTo(50_000L)

            state.onDragEnd()
            assertThat(committedPosition).isEqualTo(50_000L)

            advanceTimeBy(600L)
            assertThat(awaitItem()).isNull()
            assertThat(state.isSeeking).isFalse()
        }
    }
}
