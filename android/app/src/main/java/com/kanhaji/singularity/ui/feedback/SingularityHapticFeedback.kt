package com.kanhaji.singularity.ui.feedback

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalView

val LocalHapticFeedbackEnabled = compositionLocalOf { true }

@Composable
fun WithoutHapticFeedback(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalHapticFeedbackEnabled provides false, content = content)
}

@Composable
fun rememberHapticClick(
    enabled: Boolean = true,
    onClick: () -> Unit
): () -> Unit {
    val view = LocalView.current
    val isGloballyEnabled = LocalHapticFeedbackEnabled.current
    return remember(view, onClick, enabled, isGloballyEnabled) {
        if (!enabled || !isGloballyEnabled) {
            onClick
        } else {
            {
                HapticFeedbackManager.performVirtualKey(view)
                onClick()
            }
        }
    }
}

@Composable
fun rememberHapticClickOrNull(
    enabled: Boolean = true,
    onClick: (() -> Unit)?
): (() -> Unit)? {
    if (onClick == null) return null
    return rememberHapticClick(enabled = enabled, onClick = onClick)
}

fun Modifier.hapticClickable(
    enabled: Boolean = true,
    hapticFeedback: Boolean = true,
    onClick: () -> Unit
): Modifier = composed {
    val clickHandler = rememberHapticClick(enabled = hapticFeedback, onClick = onClick)
    clickable(enabled = enabled, onClick = clickHandler)
}

@Composable
fun rememberHapticLongClick(
    enabled: Boolean = true,
    onLongClick: () -> Unit
): () -> Unit {
    val view = LocalView.current
    val isGloballyEnabled = LocalHapticFeedbackEnabled.current
    return remember(view, onLongClick, enabled, isGloballyEnabled) {
        if (!enabled || !isGloballyEnabled) {
            onLongClick
        } else {
            {
                HapticFeedbackManager.performLongPress(view)
                onLongClick()
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.hapticCombinedClickable(
    enabled: Boolean = true,
    hapticFeedback: Boolean = true,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
): Modifier = composed {
    val clickHandler = rememberHapticClick(enabled = hapticFeedback, onClick = onClick)
    val longClickHandler = onLongClick?.let {
        rememberHapticLongClick(enabled = hapticFeedback, onLongClick = it)
    }
    combinedClickable(
        enabled = enabled,
        onClick = clickHandler,
        onLongClick = longClickHandler
    )
}
