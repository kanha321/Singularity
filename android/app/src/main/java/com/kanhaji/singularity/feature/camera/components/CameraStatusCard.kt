package com.kanhaji.singularity.feature.camera.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kanhaji.core.designsystem.cards.SurfaceCard

@Composable
fun CameraStatusCard(
    isPcConnected: Boolean,
    isStreaming: Boolean,
    selectedLens: String,
    isMicActive: Boolean = false,
    modifier: Modifier = Modifier
) {
    val activeColor = when {
        !isPcConnected -> MaterialTheme.colorScheme.outlineVariant
        isStreaming -> Color(0xFF4ADE80)
        else -> MaterialTheme.colorScheme.primary
    }

    val statusBorderColor by animateColorAsState(
        targetValue = when {
            !isPcConnected -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            isStreaming -> Color(0xFF4ADE80).copy(alpha = 0.6f)
            else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        },
        label = "status_border"
    )

    SurfaceCard(
        color = Color.Transparent,
        borderWidth = 1.dp,
        borderColor = statusBorderColor,
        cornerRadius = 20.dp,
        elevation = 0.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(activeColor.copy(alpha = 0.12f))
                    .border(1.dp, activeColor.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isStreaming) Icons.Outlined.Videocam else Icons.Outlined.VideocamOff,
                    contentDescription = null,
                    tint = activeColor,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when {
                        !isPcConnected -> "Disconnected"
                        isStreaming -> "Webcam Streaming Active"
                        else -> "Ready to Stream"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = when {
                        !isPcConnected -> "Connect phone to PC over Highway first"
                        isStreaming -> {
                            val micInfo = if (isMicActive) " • Mic Active" else ""
                            "Broadcasting ${selectedLens.replaceFirstChar { it.uppercase() }} lens to /dev/video10$micInfo"
                        }
                        else -> "Tap start to activate camera and virtual microphone"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        }
    }
}
