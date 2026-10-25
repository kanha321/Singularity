package com.kanhaji.singularity.feature.player.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

data class SpeedUiState(
    val isHolding: Boolean = false,
    val isLocked: Boolean = false,
    val speed: Float = 1.0f,
    val isHoldingOnLeft: Boolean = false,
    val isHoveringLeftLock: Boolean = false,
    val isHoveringRightLock: Boolean = false
)

class PlayerSpeedState(
    private val baselineSpeed: Float = 2.0f,
    private val onPlaybackSpeedChange: (speed: Float) -> Unit = {}
) {
    private val _uiState = MutableStateFlow(SpeedUiState())
    val uiState: StateFlow<SpeedUiState> = _uiState.asStateFlow()

    private var normalSpeed = 1.0f

    val isHoldingOrLocked: Boolean
        get() = _uiState.value.isHolding || _uiState.value.isLocked

    fun onHoldStart(isLeft: Boolean, currentSpeed: Float = 1.0f) {
        if (!_uiState.value.isLocked) {
            normalSpeed = currentSpeed
        }
        _uiState.value = SpeedUiState(
            isHolding = true,
            isLocked = false,
            speed = baselineSpeed,
            isHoldingOnLeft = isLeft,
            isHoveringLeftLock = false,
            isHoveringRightLock = false
        )
        onPlaybackSpeedChange(baselineSpeed)
    }

    fun onSlideDelta(delta: Float) {
        val current = _uiState.value
        if (!current.isHolding) return
        val rawTarget = (baselineSpeed + delta).coerceIn(0.25f, 4.0f)
        val stepped = (rawTarget * 4f).roundToInt() / 4f
        if (stepped != current.speed) {
            _uiState.value = current.copy(speed = stepped)
            onPlaybackSpeedChange(stepped)
        }
    }

    fun onHoverLock(isLeft: Boolean, isRight: Boolean) {
        val current = _uiState.value
        if (!current.isHolding) return
        if (current.isHoveringLeftLock != isLeft || current.isHoveringRightLock != isRight) {
            _uiState.value = current.copy(
                isHoveringLeftLock = isLeft,
                isHoveringRightLock = isRight
            )
        }
    }

    fun onHoldEnd() {
        val current = _uiState.value
        if (!current.isHolding) return
        val isLockedNow = (current.isHoldingOnLeft && current.isHoveringLeftLock) ||
            (!current.isHoldingOnLeft && current.isHoveringRightLock)

        if (isLockedNow) {
            _uiState.value = current.copy(
                isHolding = false,
                isLocked = true,
                isHoveringLeftLock = false,
                isHoveringRightLock = false
            )
        } else {
            _uiState.value = SpeedUiState()
            onPlaybackSpeedChange(normalSpeed)
        }
    }

    fun onUnlock() {
        _uiState.value = SpeedUiState()
        onPlaybackSpeedChange(normalSpeed)
    }
}
