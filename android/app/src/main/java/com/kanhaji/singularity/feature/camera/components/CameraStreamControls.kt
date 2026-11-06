package com.kanhaji.singularity.feature.camera.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kanhaji.singularity.ui.components.AppSnackbar
import com.kanhaji.singularity.ui.components.Button

@Composable
fun CameraStreamControls(
    isPcConnected: Boolean,
    isStreaming: Boolean,
    onToggleStreaming: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val stopColor = Color(0xFFEF4444)
        val containerColor = when {
            isStreaming -> stopColor
            isPcConnected -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        }
        val contentColor = when {
            isStreaming -> Color.White
            isPcConnected -> MaterialTheme.colorScheme.onPrimary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }

        Button(
            onClick = {
                if (!isPcConnected) {
                    AppSnackbar.show("PC is currently offline")
                    return@Button
                }
                onToggleStreaming()
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = containerColor,
                contentColor = contentColor
            ),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {
            Icon(
                imageVector = if (isStreaming) Icons.Outlined.Stop else Icons.Outlined.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isStreaming) "Stop Webcam Stream" else "Start Webcam Stream",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (!isPcConnected) {
            Text(
                text = "Connect phone to PC over Highway to start streaming",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}
