package com.kanhaji.singularity.feature.image.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size

class ZoomableState(
    val minScale: Float = 1.0f,
    val maxScale: Float = 5.0f,
    val doubleTapScale: Float = 2.5f
) {
    var scale by mutableFloatStateOf(1.0f)
        private set

    var offset by mutableStateOf(Offset.Zero)
        private set

    val isZoomed: Boolean
        get() = scale > 1.05f

    fun onTransform(zoomChange: Float, panChange: Offset, containerSize: Size) {
        val newScale = (scale * zoomChange).coerceIn(minScale, maxScale)
        scale = newScale

        if (newScale <= 1.05f) {
            offset = Offset.Zero
            scale = 1.0f
        } else {
            val maxOffsetX = ((containerSize.width * (newScale - 1f)) / 2f).coerceAtLeast(0f)
            val maxOffsetY = ((containerSize.height * (newScale - 1f)) / 2f).coerceAtLeast(0f)

            val rawX = offset.x + panChange.x
            val rawY = offset.y + panChange.y

            offset = Offset(
                x = rawX.coerceIn(-maxOffsetX, maxOffsetX),
                y = rawY.coerceIn(-maxOffsetY, maxOffsetY)
            )
        }
    }

    fun onDoubleTap(tapOffset: Offset, containerSize: Size) {
        if (isZoomed) {
            reset()
        } else {
            scale = doubleTapScale
            val center = Offset(containerSize.width / 2f, containerSize.height / 2f)
            val diff = center - tapOffset
            val maxOffsetX = ((containerSize.width * (doubleTapScale - 1f)) / 2f).coerceAtLeast(0f)
            val maxOffsetY = ((containerSize.height * (doubleTapScale - 1f)) / 2f).coerceAtLeast(0f)

            offset = Offset(
                x = (diff.x * (doubleTapScale - 1f)).coerceIn(-maxOffsetX, maxOffsetX),
                y = (diff.y * (doubleTapScale - 1f)).coerceIn(-maxOffsetY, maxOffsetY)
            )
        }
    }

    fun reset() {
        scale = 1.0f
        offset = Offset.Zero
    }
}
