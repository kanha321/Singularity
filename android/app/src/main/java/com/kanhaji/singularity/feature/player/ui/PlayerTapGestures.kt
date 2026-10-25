package com.kanhaji.singularity.feature.player.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import com.kanhaji.singularity.feature.player.state.PlayerDoubleTapState

fun Modifier.playerTapGestures(
    doubleTapState: PlayerDoubleTapState,
    onSingleTap: () -> Unit,
    onDoubleTapSeek: (isForward: Boolean) -> Unit,
    onPlayPause: () -> Unit
): Modifier = pointerInput(doubleTapState) {
    detectTapGestures(
        onTap = { offset ->
            if (doubleTapState.isSeeking) {
                onDoubleTapSeek(offset.x > (size.width / 2f))
            } else {
                onSingleTap()
            }
        },
        onDoubleTap = { offset ->
            val xRatio = offset.x / size.width
            when {
                xRatio < 0.40f -> onDoubleTapSeek(false)
                xRatio > 0.60f -> onDoubleTapSeek(true)
                else -> onPlayPause()
            }
        }
    )
}
