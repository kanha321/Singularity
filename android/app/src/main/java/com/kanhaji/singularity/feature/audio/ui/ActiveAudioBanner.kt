package com.kanhaji.singularity.feature.audio.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kanhaji.singularity.feature.audio.model.ActiveAudioRouteInfo

@Composable
fun ActiveAudioBanner(activeRoute: ActiveAudioRouteInfo?, modifier: Modifier = Modifier) {
    if (activeRoute == null || !activeRoute.isPlaying) return

    val context = LocalContext.current
    val appLabel = AppNameResolver.resolve(context, activeRoute.sourceApp)
    val appSuffix = if (appLabel != null) " (via $appLabel)" else ""

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF4CAF50).copy(alpha = 0.12f),
        border = BorderStroke(1.dp, Color(0xFF4CAF50).copy(alpha = 0.35f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF4CAF50)))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "ACTIVE OUTPUT: ${activeRoute.outputDeviceLabel}$appSuffix",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF4CAF50)
            )
        }
    }
}

