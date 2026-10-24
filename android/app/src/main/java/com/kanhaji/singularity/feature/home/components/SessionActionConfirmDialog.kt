package com.kanhaji.singularity.feature.home.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

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
    val confirmColor = if (isLock) Color(0xFFFBBF24) else Color(0xFFF87171)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Text(text = message, style = MaterialTheme.typography.bodyMedium)
        },
        confirmButton = {
            OutlinedButton(
                onClick = {
                    onConfirm(action)
                    onDismiss()
                },
                border = BorderStroke(1.dp, confirmColor),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = confirmColor)
            ) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}
