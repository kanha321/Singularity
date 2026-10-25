package com.kanhaji.singularity.feature.image.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import com.kanhaji.singularity.feature.image.state.ZoomableState

fun Modifier.zoomableGestures(
    state: ZoomableState,
    containerSize: Size,
    onSingleTap: () -> Unit = {}
): Modifier = this
    .pointerInput(containerSize) {
        detectTapGestures(
            onTap = { onSingleTap() },
            onDoubleTap = { tapOffset ->
                state.onDoubleTap(tapOffset, containerSize)
            }
        )
    }
    .pointerInput(state, containerSize) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            do {
                val event = awaitPointerEvent()
                val canceled = event.changes.any { it.isConsumed }
                if (!canceled) {
                    val pointerCount = event.changes.size
                    if (pointerCount >= 2 || state.isZoomed) {
                        val zoom = event.calculateZoom()
                        val pan = event.calculatePan()

                        if (zoom != 1f || pan != Offset.Zero) {
                            state.onTransform(zoom, pan, containerSize)
                            event.changes.forEach {
                                if (it.positionChanged()) {
                                    it.consume()
                                }
                            }
                        }
                    }
                }
            } while (event.changes.any { it.pressed })
        }
    }
