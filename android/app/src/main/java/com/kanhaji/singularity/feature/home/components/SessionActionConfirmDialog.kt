package com.kanhaji.singularity.feature.home.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.runtime.Composable
import com.kanhaji.singularity.ui.components.SingularityConfirmDialog

enum class SessionConfirmAction {
    LOCK,
    LOGOUT
}

@Composable
fun SessionActionConfirmDialog(
    action: SessionConfirmAction?,
    onDismiss: () -> Unit,
    onConfirm: (SessionConfirmAction) -> Unit
) {
    if (action == null) return

    val isLock = action == SessionConfirmAction.LOCK
    val title = if (isLock) "Lock PC Screen" else "Log Out of PC"
    val message = if (isLock) {
        "Are you sure you want to lock your desktop screen?"
    } else {
        "Are you sure you want to log out and terminate your desktop session?"
    }
    val confirmText = if (isLock) "Lock" else "Log Out"
    val icon = if (isLock) Icons.Outlined.Lock else Icons.AutoMirrored.Outlined.Logout

    SingularityConfirmDialog(
        title = title,
        icon = icon,
        message = message,
        confirmText = confirmText,
        isDestructive = !isLock,
        onDismissRequest = onDismiss,
        onConfirm = {
            onConfirm(action)
            onDismiss()
        }
    )
}
