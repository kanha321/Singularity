package com.kanhaji.singularity.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.kanhaji.core.designsystem.feedback.rememberHapticClick

@Composable
fun SingularityConfirmDialog(
    title: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    message: String? = null,
    confirmText: String = "Confirm",
    dismissText: String = "Cancel",
    isDestructive: Boolean = false,
    isLoading: Boolean = false,
    confirmEnabled: Boolean = true,
    onConfirm: () -> Unit,
    content: (@Composable ColumnScope.() -> Unit)? = null
) {
    val accent = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    SingularityDialog(
        onDismissRequest = { if (!isLoading) onDismissRequest() },
        modifier = modifier,
        icon = icon?.let {
            {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(44.dp)
                        .border(BorderStroke(1.dp, accent.copy(alpha = 0.45f)), shape = RoundedCornerShape(12.dp))
                ) {
                    Icon(
                        imageVector = it,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (message != null) Text(message)
                content?.invoke(this)
            }
        },
        confirmButton = {
            OutlinedButton(
                onClick = rememberHapticClick(onClick = onConfirm),
                enabled = confirmEnabled && !isLoading,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, if (confirmEnabled) accent else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = accent)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = accent
                    )
                } else {
                    Text(confirmText)
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = rememberHapticClick(onClick = onDismissRequest),
                enabled = !isLoading,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(dismissText, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}
