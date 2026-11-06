package com.kanhaji.singularity.feature.camera.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kanhaji.core.designsystem.cards.SurfaceCard

@Composable
fun StreamCapabilitiesCard(modifier: Modifier = Modifier) {
    SurfaceCard(
        color = Color.Transparent,
        borderWidth = 1.dp,
        borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        cornerRadius = 16.dp,
        elevation = 0.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StreamFeatureRow(
                icon = Icons.Outlined.Videocam,
                title = "Video Output",
                subtitle = "1080p @ 30 FPS • Hardware H.264 (/dev/video10)"
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
            StreamFeatureRow(
                icon = Icons.Outlined.Mic,
                title = "Virtual Microphone",
                subtitle = "PipeWire node • Echo cancellation"
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
            StreamFeatureRow(
                icon = Icons.Outlined.Bolt,
                title = "Ultra-Low Latency",
                subtitle = "< 30ms • Directly piped to Chromium & Google Meet"
            )
        }
    }
}

