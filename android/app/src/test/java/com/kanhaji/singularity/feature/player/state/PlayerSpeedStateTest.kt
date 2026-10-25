package com.kanhaji.singularity.feature.player.state

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlayerSpeedStateTest {

    @Test
    fun onHoldStart_defaultSpeed_emitsHoldingAt2x() {
        var appliedSpeed = -1f
        val state = PlayerSpeedState(
            baselineSpeed = 2.0f,
            onPlaybackSpeedChange = { appliedSpeed = it }
        )

        state.onHoldStart(isLeft = true, currentSpeed = 1.0f)

        val ui = state.uiState.value
        assertThat(ui.isHolding).isTrue()
        assertThat(ui.speed).isEqualTo(2.0f)
        assertThat(ui.isHoldingOnLeft).isTrue()
        assertThat(appliedSpeed).isEqualTo(2.0f)
    }

    @Test
    fun onSlideDelta_whileHolding_adjustsSpeedInQuarterSteps() {
        var appliedSpeed = -1f
        val state = PlayerSpeedState(
            baselineSpeed = 2.0f,
            onPlaybackSpeedChange = { appliedSpeed = it }
        )

        state.onHoldStart(isLeft = false, currentSpeed = 1.0f)
        state.onSlideDelta(0.55f) // 2.0 + 0.55 = 2.55 -> rounds to 2.50

        assertThat(state.uiState.value.speed).isEqualTo(2.5f)
        assertThat(appliedSpeed).isEqualTo(2.5f)
    }

    @Test
    fun onSlideDelta_notHolding_ignoresDelta() {
        var appliedSpeed = -1f
        val state = PlayerSpeedState(
            baselineSpeed = 2.0f,
            onPlaybackSpeedChange = { appliedSpeed = it }
        )

        state.onSlideDelta(1.0f)
        assertThat(state.uiState.value.isHolding).isFalse()
        assertThat(appliedSpeed).isEqualTo(-1f)
    }

    @Test
    fun onHoverLock_updatesHoverStates() {
        val state = PlayerSpeedState()
        state.onHoldStart(isLeft = true)

        state.onHoverLock(isLeft = true, isRight = false)
        assertThat(state.uiState.value.isHoveringLeftLock).isTrue()
        assertThat(state.uiState.value.isHoveringRightLock).isFalse()
    }

    @Test
    fun onHoldEnd_withoutHover_restoresNormalSpeed() {
        var appliedSpeed = -1f
        val state = PlayerSpeedState(
            onPlaybackSpeedChange = { appliedSpeed = it }
        )

        state.onHoldStart(isLeft = false, currentSpeed = 1.25f)
        state.onHoldEnd()

        assertThat(state.uiState.value.isHolding).isFalse()
        assertThat(state.uiState.value.isLocked).isFalse()
        assertThat(appliedSpeed).isEqualTo(1.25f)
    }

    @Test
    fun onHoldEnd_withLockHover_locksSpeed() {
        var appliedSpeed = -1f
        val state = PlayerSpeedState(
            onPlaybackSpeedChange = { appliedSpeed = it }
        )

        state.onHoldStart(isLeft = true, currentSpeed = 1.0f)
        state.onSlideDelta(0.5f) // 2.5x
        state.onHoverLock(isLeft = true, isRight = false)
        state.onHoldEnd()

        val ui = state.uiState.value
        assertThat(ui.isHolding).isFalse()
        assertThat(ui.isLocked).isTrue()
        assertThat(ui.speed).isEqualTo(2.5f)
        assertThat(appliedSpeed).isEqualTo(2.5f) // remains at 2.5x!
    }

    @Test
    fun onUnlock_whileLocked_restoresNormalSpeedAndUnlocks() {
        var appliedSpeed = -1f
        val state = PlayerSpeedState(
            onPlaybackSpeedChange = { appliedSpeed = it }
        )

        state.onHoldStart(isLeft = false, currentSpeed = 1.0f)
        state.onHoverLock(isLeft = false, isRight = true)
        state.onHoldEnd()

        assertThat(state.uiState.value.isLocked).isTrue()

        state.onUnlock()
        assertThat(state.uiState.value.isLocked).isFalse()
        assertThat(appliedSpeed).isEqualTo(1.0f)
    }
}
