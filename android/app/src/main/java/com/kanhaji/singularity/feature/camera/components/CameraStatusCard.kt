package com.kanhaji.singularity.feature.camera.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun CameraStatusCard(
    isPcConnected: Boolean,
    isStreaming: Boolean,
    selectedLens: String
) {
    val statusColor by animateColorAsState(
        targetValue = when {
            !isPcConnected -> MaterialTheme.colorScheme.outline
            isStreaming -> Color(0xFF4CAF50)
            else -> MaterialTheme.colorScheme.primary
        },
        label = "status_color"
    )

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when {
                        !isPcConnected -> "Disconnected"
                        isStreaming -> "Active Webcam Streaming"
                        else -> "Ready to Stream"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = when {
                        !isPcConnected -> "Connect phone to PC first"
                        isStreaming -> "Broadcasting ${selectedLens.replaceFirstChar { it.uppercase() }} lens to /dev/video10"
                        else -> "Tap start to activate camera and virtual mic"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

