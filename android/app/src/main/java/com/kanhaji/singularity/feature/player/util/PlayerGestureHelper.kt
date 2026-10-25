package com.kanhaji.singularity.feature.player.util

import kotlin.math.abs
import kotlin.math.roundToInt

enum class DragGestureType {
    UNDECIDED,
    HORIZONTAL_SEEK,
    VERTICAL_BRIGHTNESS,
    VERTICAL_VOLUME
}

object PlayerGestureHelper {
    const val EDGE_DEADZONE_RATIO = 0.05f
    const val SLOPE_RATIO_THRESHOLD = 2.0f

    fun isWithinAllowedBounds(x: Float, width: Float): Boolean {
        if (width <= 0f) return false
        val minX = width * EDGE_DEADZONE_RATIO
        val maxX = width * (1f - EDGE_DEADZONE_RATIO)
        return x in minX..maxX
    }

    fun classifyDrag(
        startX: Float,
        width: Float,
        totalDragX: Float,
        totalDragY: Float,
        touchSlop: Float
    ): DragGestureType {
        val absX = abs(totalDragX)
        val absY = abs(totalDragY)
        if (absX < touchSlop && absY < touchSlop) {
            return DragGestureType.UNDECIDED
        }

        return when {
            absY > SLOPE_RATIO_THRESHOLD * absX -> {
                if (startX < width / 2f) {
                    DragGestureType.VERTICAL_BRIGHTNESS
                } else {
                    DragGestureType.VERTICAL_VOLUME
                }
            }
            absX > SLOPE_RATIO_THRESHOLD * absY -> {
                DragGestureType.HORIZONTAL_SEEK
            }
            else -> DragGestureType.UNDECIDED
        }
    }

    fun calculateVerticalDeltaPercent(
        dragDeltaY: Float,
        screenHeight: Float
    ): Int {
        if (screenHeight <= 0f) return 0
        val fraction = -dragDeltaY / screenHeight
        return (fraction * 100f).roundToInt()
    }

    fun calculateSpeedDelta(
        anchorY: Float,
        currentY: Float,
        screenHeight: Float
    ): Float {
        if (screenHeight <= 0f) return 0f
        val deltaFromAnchor = -(currentY - anchorY)
        return (deltaFromAnchor / (screenHeight * 0.35f).coerceAtLeast(1f)) * 2.0f
    }

    fun isHoveringEdgeLock(
        currentX: Float,
        currentY: Float,
        width: Float,
        height: Float,
        isLeft: Boolean
    ): Boolean {
        if (width <= 0f || height <= 0f) return false
        val inVerticalZone = currentY in (height * 0.30f)..(height * 0.70f)
        if (!inVerticalZone) return false
        return if (isLeft) currentX < width * 0.15f else currentX > width * 0.85f
    }
}
