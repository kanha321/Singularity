package com.kanhaji.singularity.feature.auth.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kanhaji.core.designsystem.cards.SurfaceCard

@Composable
fun AuthTimeoutsCard(
    promptTimeoutSecs: Int,
    notifyTimeoutSecs: Int,
    onPromptTimeoutChange: (Int) -> Unit,
    onNotifyTimeoutChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    SurfaceCard(
        color = Color.Transparent,
        borderWidth = 1.dp,
        borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        cornerRadius = 16.dp,
        elevation = 0.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TimeoutRow(
                title = "Prompt Timeout",
                description = "Wait time for immediate popup",
                seconds = promptTimeoutSecs,
                options = listOf(10, 15, 20),
                onSelect = onPromptTimeoutChange
            )
            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
            TimeoutRow(
                title = "Notify Timeout",
                description = "Wait time before password fallback",
                seconds = notifyTimeoutSecs,
                options = listOf(20, 30, 45),
                onSelect = onNotifyTimeoutChange
            )
        }
    }
}

@Composable
private fun TimeoutRow(
    title: String,
    description: String,
    seconds: Int,
    options: List<Int>,
    onSelect: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            options.forEach { sec ->
                val isSelected = sec == seconds
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelect(sec) },
                    label = { Text("${sec}s", fontSize = 11.sp) }
                )
            }
        }
    }
}
