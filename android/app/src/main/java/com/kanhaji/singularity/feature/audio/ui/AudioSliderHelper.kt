package com.kanhaji.singularity.feature.audio.ui

import kotlin.math.roundToInt

object AudioSliderHelper {
    /**
     * Converts a horizontal touch X coordinate within a total width into a 0..100 percentage.
     */
    fun computeVolumePercent(touchX: Float, totalWidthPx: Float): Int {
        if (totalWidthPx <= 0f) return 0
        val fraction = (touchX / totalWidthPx).coerceIn(0f, 1f)
        return (fraction * 100f).roundToInt()
    }

    /**
     * Formats volume status text for display.
     */
    fun formatVolumeSubtitle(volume: Int, isMuted: Boolean): String {
        return if (isMuted) "Muted" else "$volume% volume"
    }
}

