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
class PlayerVolumeBrightnessStateTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun onDragStart_volume_emitsInitialStateWithAlignLeft() = runTest {
        val state = PlayerVolumeBrightnessState(scope = this)
        state.onDragStart(VolumeBrightnessType.VOLUME, startingPercent = 50)

        val uiState = state.uiState.value
        assertThat(uiState).isNotNull()
        assertThat(uiState?.type).isEqualTo(VolumeBrightnessType.VOLUME)
        assertThat(uiState?.percent).isEqualTo(50)
        assertThat(uiState?.alignOnRight).isFalse()
    }

    @Test
    fun onDragStart_brightness_emitsInitialStateWithAlignRight() = runTest {
        val state = PlayerVolumeBrightnessState(scope = this)
        state.onDragStart(VolumeBrightnessType.BRIGHTNESS, startingPercent = 70)

        val uiState = state.uiState.value
        assertThat(uiState).isNotNull()
        assertThat(uiState?.type).isEqualTo(VolumeBrightnessType.BRIGHTNESS)
        assertThat(uiState?.percent).isEqualTo(70)
        assertThat(uiState?.alignOnRight).isTrue()
    }

    @Test
    fun onDragDelta_volume_updatesPercentAndPreservesAlignment() = runTest {
        var lastVolume = -1
        val state = PlayerVolumeBrightnessState(
            scope = this,
            onVolumeChange = { lastVolume = it }
        )

        state.onDragStart(VolumeBrightnessType.VOLUME, startingPercent = 40)
        state.onDragDelta(VolumeBrightnessType.VOLUME, deltaPercent = 15)

        assertThat(state.uiState.value?.percent).isEqualTo(55)
        assertThat(state.uiState.value?.alignOnRight).isFalse()
        assertThat(lastVolume).isEqualTo(55)
    }

    @Test
    fun onDragDelta_brightness_clampsBetweenZeroAndHundred() = runTest {
        var lastBrightness = -1
        val state = PlayerVolumeBrightnessState(
            scope = this,
            onBrightnessChange = { lastBrightness = it }
        )

        state.onDragStart(VolumeBrightnessType.BRIGHTNESS, startingPercent = 90)
        state.onDragDelta(VolumeBrightnessType.BRIGHTNESS, deltaPercent = 25)
        assertThat(state.uiState.value?.percent).isEqualTo(100)
        assertThat(lastBrightness).isEqualTo(100)

        state.onDragDelta(VolumeBrightnessType.BRIGHTNESS, deltaPercent = -120)
        assertThat(state.uiState.value?.percent).isEqualTo(0)
        assertThat(lastBrightness).isEqualTo(0)
    }

    @Test
    fun onDragEnd_afterTimeout_dismissesUiState() = runTest {
        val state = PlayerVolumeBrightnessState(
            scope = this,
            dismissTimeoutMillis = 1000L
        )

        state.uiState.test {
            assertThat(awaitItem()).isNull()

            state.onDragStart(VolumeBrightnessType.VOLUME, startingPercent = 30)
            assertThat(awaitItem()?.percent).isEqualTo(30)

            state.onDragEnd()
            advanceTimeBy(1100L)
            assertThat(awaitItem()).isNull()
        }
    }

    @Test
    fun dismissImmediately_clearsStateAndCancelsJob() = runTest {
        val state = PlayerVolumeBrightnessState(scope = this)
        state.onDragStart(VolumeBrightnessType.BRIGHTNESS, startingPercent = 50)
        assertThat(state.uiState.value).isNotNull()

        state.dismissImmediately()
        assertThat(state.uiState.value).isNull()
    }
}
