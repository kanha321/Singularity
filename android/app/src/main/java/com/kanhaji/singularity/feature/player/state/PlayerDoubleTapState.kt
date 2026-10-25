package com.kanhaji.singularity.feature.player.state

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DoubleTapSeekEvent(
    val isForward: Boolean,
    val accumulatedSeconds: Int
)

class PlayerDoubleTapState(
    private val scope: CoroutineScope,
    private val seekStepSeconds: Int = 10,
    private val resetDelayMillis: Long = 650L,
    private val onSeek: (deltaSeconds: Int) -> Unit
) {
    private val _seekEvent = MutableStateFlow<DoubleTapSeekEvent?>(null)
    val seekEvent: StateFlow<DoubleTapSeekEvent?> = _seekEvent.asStateFlow()

    private var resetJob: Job? = null

    fun handleDoubleTap(isForward: Boolean) {
        resetJob?.cancel()

        val current = _seekEvent.value
        val newAccumulated = if (current != null && current.isForward == isForward) {
            current.accumulatedSeconds + seekStepSeconds
        } else {
            seekStepSeconds
        }

        _seekEvent.value = DoubleTapSeekEvent(
            isForward = isForward,
            accumulatedSeconds = newAccumulated
        )

        val delta = if (isForward) seekStepSeconds else -seekStepSeconds
        onSeek(delta)

        resetJob = scope.launch {
            delay(resetDelayMillis)
            _seekEvent.value = null
        }
    }

    val isSeeking: Boolean
        get() = _seekEvent.value != null

    fun cancel() {
        resetJob?.cancel()
        _seekEvent.value = null
    }
}
