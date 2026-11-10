package com.kanhaji.singularity.feature.audio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Smartphone
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
fun PhoneAudioRow(isSelected: Boolean, isActiveOutput: Boolean = false, onSelect: () -> Unit) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    val cardBg = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)

    SurfaceCard(
        onClick = onSelect,
        color = cardBg,
        borderWidth = if (isSelected) 1.5.dp else 1.dp,
        borderColor = borderColor,
        cornerRadius = 16.dp,
        elevation = 0.dp,
        modifier = Modifier.fillMaxWidth().height(68.dp)
    ) {
        Row(modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .border(1.dp, if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Smartphone, null, Modifier.size(22.dp), tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("This Phone", style = MaterialTheme.typography.titleMedium, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium, fontSize = 15.sp)
                    if (isActiveOutput) {
                        Spacer(modifier = Modifier.width(6.dp))
                        AudioActiveBadge()
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text("Phone speakers or wired/BT headset", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            Box(modifier = Modifier.size(22.dp).clip(CircleShape).background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent).border(1.5.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape), contentAlignment = Alignment.Center) {
                if (isSelected) Icon(Icons.Outlined.Check, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}

