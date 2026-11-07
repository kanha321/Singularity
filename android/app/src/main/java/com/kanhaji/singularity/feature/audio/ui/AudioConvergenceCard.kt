package com.kanhaji.singularity.feature.audio.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kanhaji.core.designsystem.cards.SurfaceCard

@Composable
fun AudioConvergenceCard(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (enabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    val cardBg = if (enabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
    else Color.Transparent

    SurfaceCard(
        onClick = { onToggle(!enabled) },
        color = cardBg,
        borderWidth = 1.dp,
        borderColor = borderColor,
        cornerRadius = 16.dp,
        elevation = 0.dp,
        modifier = modifier
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(42.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (enabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .border(1.dp, if (enabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Headphones, null, Modifier.size(22.dp), tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Listen to Laptop Audio", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text("Play PC sound through phone speakers or earbuds", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
                Switch(checked = enabled, onCheckedChange = onToggle)
            }

            AnimatedVisibility(visible = enabled, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), modifier = Modifier.padding(bottom = 10.dp))
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF4CAF50)))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Live PCM Stream Active", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium, color = Color(0xFF4CAF50))
                        }
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)) {
                                Icon(Icons.Outlined.GraphicEq, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("48kHz • Jitter-Free", style = MaterialTheme.typography.labelSmall, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
