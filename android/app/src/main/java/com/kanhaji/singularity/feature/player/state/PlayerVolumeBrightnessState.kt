package com.kanhaji.singularity.feature.player.state

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class VolumeBrightnessType {
    VOLUME,
    BRIGHTNESS
}

data class VolumeBrightnessUiState(
    val type: VolumeBrightnessType,
    val percent: Int,
    val alignOnRight: Boolean = (type == VolumeBrightnessType.BRIGHTNESS)
)

class PlayerVolumeBrightnessState(
    private val scope: CoroutineScope,
    private val dismissTimeoutMillis: Long = 1200L,
    private val onVolumeChange: (percent: Int) -> Unit = {},
    private val onBrightnessChange: (percent: Int) -> Unit = {}
) {
    private val _uiState = MutableStateFlow<VolumeBrightnessUiState?>(null)
    val uiState: StateFlow<VolumeBrightnessUiState?> = _uiState.asStateFlow()

    private var dismissJob: Job? = null
    private var initialPercent = 0

    fun onDragStart(
        type: VolumeBrightnessType,
        startingPercent: Int,
        alignOnRight: Boolean = (type == VolumeBrightnessType.BRIGHTNESS)
    ) {
        dismissJob?.cancel()
        initialPercent = startingPercent.coerceIn(0, 100)
        _uiState.value = VolumeBrightnessUiState(
            type = type,
            percent = initialPercent,
            alignOnRight = alignOnRight
        )
    }

    fun onDragDelta(type: VolumeBrightnessType, deltaPercent: Int) {
        dismissJob?.cancel()
        val newPercent = (initialPercent + deltaPercent).coerceIn(0, 100)
        val currentAlign = _uiState.value?.alignOnRight ?: (type == VolumeBrightnessType.BRIGHTNESS)
        _uiState.value = VolumeBrightnessUiState(
            type = type,
            percent = newPercent,
            alignOnRight = currentAlign
        )
        when (type) {
            VolumeBrightnessType.VOLUME -> onVolumeChange(newPercent)
            VolumeBrightnessType.BRIGHTNESS -> onBrightnessChange(newPercent)
        }
    }

    fun onDragEnd() {
        dismissJob?.cancel()
        dismissJob = scope.launch {
            delay(dismissTimeoutMillis)
            _uiState.value = null
        }
    }

    fun dismissImmediately() {
        dismissJob?.cancel()
        _uiState.value = null
    }
}
