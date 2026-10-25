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
class PlayerDoubleTapStateTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun handleDoubleTap_forward_emitsForward10AndCallsSeek10() = runTest {
        val seekDeltas = mutableListOf<Int>()
        val state = PlayerDoubleTapState(
            scope = this,
            seekStepSeconds = 10,
            resetDelayMillis = 650L,
            onSeek = { seekDeltas.add(it) }
        )

        state.handleDoubleTap(isForward = true)

        val event = state.seekEvent.value
        assertThat(event).isNotNull()
        assertThat(event?.isForward).isTrue()
        assertThat(event?.accumulatedSeconds).isEqualTo(10)
        assertThat(seekDeltas).containsExactly(10)
        assertThat(state.isSeeking).isTrue()
    }

    @Test
    fun handleDoubleTap_backward_emitsBackward10AndCallsSeekMinus10() = runTest {
        val seekDeltas = mutableListOf<Int>()
        val state = PlayerDoubleTapState(
            scope = this,
            seekStepSeconds = 10,
            resetDelayMillis = 650L,
            onSeek = { seekDeltas.add(it) }
        )

        state.handleDoubleTap(isForward = false)

        val event = state.seekEvent.value
        assertThat(event).isNotNull()
        assertThat(event?.isForward).isFalse()
        assertThat(event?.accumulatedSeconds).isEqualTo(10)
        assertThat(seekDeltas).containsExactly(-10)
        assertThat(state.isSeeking).isTrue()
    }

    @Test
    fun handleDoubleTap_multipleChained_accumulatesSeconds() = runTest {
        val seekDeltas = mutableListOf<Int>()
        val state = PlayerDoubleTapState(
            scope = this,
            seekStepSeconds = 10,
            resetDelayMillis = 650L,
            onSeek = { seekDeltas.add(it) }
        )

        state.handleDoubleTap(isForward = true)
        advanceTimeBy(200L)
        state.handleDoubleTap(isForward = true)
        advanceTimeBy(200L)
        state.handleDoubleTap(isForward = true)

        val event = state.seekEvent.value
        assertThat(event?.accumulatedSeconds).isEqualTo(30)
        assertThat(seekDeltas).containsExactly(10, 10, 10)
    }

    @Test
    fun handleDoubleTap_directionChange_resetsAccumulation() = runTest {
        val seekDeltas = mutableListOf<Int>()
        val state = PlayerDoubleTapState(
            scope = this,
            seekStepSeconds = 10,
            resetDelayMillis = 650L,
            onSeek = { seekDeltas.add(it) }
        )

        state.handleDoubleTap(isForward = true)
        state.handleDoubleTap(isForward = true)
        assertThat(state.seekEvent.value?.accumulatedSeconds).isEqualTo(20)

        state.handleDoubleTap(isForward = false)
        val event = state.seekEvent.value
        assertThat(event?.isForward).isFalse()
        assertThat(event?.accumulatedSeconds).isEqualTo(10)
        assertThat(seekDeltas).containsExactly(10, 10, -10)
    }

    @Test
    fun handleDoubleTap_afterTimeout_resetsToNull() = runTest {
        val state = PlayerDoubleTapState(
            scope = this,
            seekStepSeconds = 10,
            resetDelayMillis = 650L,
            onSeek = {}
        )

        state.seekEvent.test {
            assertThat(awaitItem()).isNull()

            state.handleDoubleTap(isForward = true)
            assertThat(awaitItem()?.accumulatedSeconds).isEqualTo(10)

            advanceTimeBy(700L)
            assertThat(awaitItem()).isNull()
            assertThat(state.isSeeking).isFalse()
        }
    }

    @Test
    fun cancel_whileActive_clearsStateImmediately() = runTest {
        val state = PlayerDoubleTapState(
            scope = this,
            seekStepSeconds = 10,
            resetDelayMillis = 650L,
            onSeek = {}
        )

        state.handleDoubleTap(isForward = true)
        assertThat(state.isSeeking).isTrue()

        state.cancel()
        assertThat(state.isSeeking).isFalse()
        assertThat(state.seekEvent.value).isNull()
    }
}
