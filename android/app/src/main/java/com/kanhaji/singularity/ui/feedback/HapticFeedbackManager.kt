package com.kanhaji.singularity.ui.feedback

import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Global coordinator for tactile haptic feedback throughout the application.
 * Debounces rapid repeated triggers and honors explicit suppression requests.
 */
object HapticFeedbackManager {
    private var lastHapticTime = 0L
    private var suppressedUntil = 0L
    private const val DEBOUNCE_MS = 40L

    fun performVirtualKey(view: View?): Boolean {
        if (view == null) return false
        val now = System.currentTimeMillis()
        if (now < suppressedUntil) return false
        if (now - lastHapticTime < DEBOUNCE_MS) return false

        lastHapticTime = now
        return view.performHapticFeedback(
            HapticFeedbackConstants.VIRTUAL_KEY,
            HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
        )
    }

    fun performLongPress(view: View?): Boolean {
        if (view == null) return false
        val now = System.currentTimeMillis()
        if (now < suppressedUntil) return false

        lastHapticTime = now
        return view.performHapticFeedback(
            HapticFeedbackConstants.LONG_PRESS,
            HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
        )
    }

    fun suppressNext(durationMs: Long = 250L) {
        suppressedUntil = System.currentTimeMillis() + durationMs
    }

    fun isSuppressed(): Boolean = System.currentTimeMillis() < suppressedUntil

    fun resetForTesting() {
        lastHapticTime = 0L
        suppressedUntil = 0L
    }
}
