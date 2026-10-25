package com.kanhaji.singularity.feature.player.state

import com.kanhaji.singularity.feature.player.util.PlayerTimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs

data class SwipeSeekUiState(
    val deltaMs: Long,
    val deltaFormatted: String,
    val targetPositionMs: Long,
    val targetTimeFormatted: String,
    val totalDurationFormatted: String,
    val progressRatio: Float
)

class PlayerSwipeSeekState(
    private val scope: CoroutineScope,
    private val dismissTimeoutMillis: Long = 600L,
    private val onSeekCommit: (targetPositionMs: Long) -> Unit = {}
) {
    private val _uiState = MutableStateFlow<SwipeSeekUiState?>(null)
    val uiState: StateFlow<SwipeSeekUiState?> = _uiState.asStateFlow()

    private var dismissJob: Job? = null
    private var startPositionMs: Long = 0L
    private var totalDurationMs: Long = 0L

    val isSeeking: Boolean
        get() = _uiState.value != null

    fun onDragStart(currentPosMs: Long, durationMs: Long) {
        dismissJob?.cancel()
        startPositionMs = currentPosMs.coerceAtLeast(0L)
        totalDurationMs = durationMs.coerceAtLeast(0L)
        updateState(deltaMs = 0L)
    }

    fun onDragDelta(dragRatio: Float) {
        dismissJob?.cancel()
        val scrubRangeMs = 120_000L
        val deltaMs = (dragRatio * scrubRangeMs).toLong()
        updateState(deltaMs)
    }

    fun onDragEnd() {
        val currentTarget = _uiState.value?.targetPositionMs
        if (currentTarget != null) {
            onSeekCommit(currentTarget)
        }
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

    private fun updateState(deltaMs: Long) {
        val targetPos = (startPositionMs + deltaMs).coerceIn(0L, totalDurationMs)
        val actualDelta = targetPos - startPositionMs
        val sign = if (actualDelta >= 0) "+" else "-"
        val formattedDelta = "[$sign${PlayerTimeUtils.formatTime(abs(actualDelta))}]"

        val progress = if (totalDurationMs > 0) {
            (targetPos.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
        } else 0f

        _uiState.value = SwipeSeekUiState(
            deltaMs = actualDelta,
            deltaFormatted = formattedDelta,
            targetPositionMs = targetPos,
            targetTimeFormatted = PlayerTimeUtils.formatTime(targetPos),
            totalDurationFormatted = PlayerTimeUtils.formatTime(totalDurationMs),
            progressRatio = progress
        )
    }
}
