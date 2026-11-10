package com.kanhaji.singularity.feature.audio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kanhaji.core.designsystem.cards.SurfaceCard
import com.kanhaji.singularity.feature.audio.route.AudioRoutePreferences

@Composable
fun AudioLatencyCard(modifier: Modifier = Modifier) {
    val isLowLatency by AudioRoutePreferences.isLowLatencyMode.collectAsState()

    val borderColor = if (isLowLatency) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    val cardBg = if (isLowLatency) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)

    SurfaceCard(
        onClick = { AudioRoutePreferences.setLowLatencyMode(!isLowLatency) },
        color = cardBg,
        borderWidth = 1.dp,
        borderColor = borderColor,
        cornerRadius = 16.dp,
        elevation = 0.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (isLowLatency) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .border(1.dp, if (isLowLatency) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isLowLatency) Icons.Outlined.Bolt else Icons.Outlined.GraphicEq,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = if (isLowLatency) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Low Latency Mode", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isLowLatency) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isLowLatency) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = if (isLowLatency) "~45ms" else "~110ms",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = if (isLowLatency) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (isLowLatency) "Ultra-low delay (~45ms) • May stutter on weak Wi-Fi" else "Stable buffer (~110ms) • Smooth crackle-free audio",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
            Switch(
                checked = isLowLatency,
                onCheckedChange = { AudioRoutePreferences.setLowLatencyMode(it) }
            )
        }
    }
}

