package com.kanhaji.singularity.feature.auth.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kanhaji.core.designsystem.cards.SurfaceCard
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun AuthSnoozeCard(
    isSnoozed: Boolean,
    snoozeUntilEpochMs: Long,
    selectedDurationMinutes: Int,
    onSetSnooze: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val amber = Color(0xFFFBBF24)
    val durations = listOf(0 to "Off", 5 to "5m", 10 to "10m", 30 to "30m", 60 to "1h")

    var remainingMillis by remember(snoozeUntilEpochMs) {
        mutableLongStateOf((snoozeUntilEpochMs - System.currentTimeMillis()).coerceAtLeast(0L))
    }

    LaunchedEffect(snoozeUntilEpochMs) {
        while (snoozeUntilEpochMs > System.currentTimeMillis()) {
            remainingMillis = (snoozeUntilEpochMs - System.currentTimeMillis()).coerceAtLeast(0L)
            delay(500L)
        }
        remainingMillis = 0L
        if (snoozeUntilEpochMs > 0L) onSetSnooze(0)
    }

    val countdownText = remember(remainingMillis) {
        val totalSecs = (remainingMillis + 999L) / 1000L
        val (h, m, s) = Triple(totalSecs / 3600, (totalSecs % 3600) / 60, totalSecs % 60)
        if (h > 0) "%d:%02d:%02d".format(Locale.getDefault(), h, m, s)
        else "%02d:%02d".format(Locale.getDefault(), m, s)
    }

    SurfaceCard(
        color = Color.Transparent,
        borderWidth = 1.dp,
        borderColor = if (isSnoozed) amber.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        cornerRadius = 16.dp,
        elevation = 0.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Global Snooze", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        if (isSnoozed) "⏸ Active ($countdownText left)" else "Bypass phone auth temporarily",
                        fontSize = 12.sp,
                        color = if (isSnoozed) amber else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isSnoozed) {
                    OutlinedButton(
                        onClick = { onSetSnooze(0) },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        border = BorderStroke(1.dp, amber),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = amber),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("Resume", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                durations.forEach { (mins, label) ->
                    val isSelected = if (!isSnoozed) mins == 0 else mins == selectedDurationMinutes
                    OutlinedButton(
                        onClick = { onSetSnooze(mins) },
                        modifier = Modifier.weight(1f).height(34.dp),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(0.dp),
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (isSelected) amber else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isSelected) amber.copy(alpha = 0.12f) else Color.Transparent,
                            contentColor = if (isSelected) amber else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text(label, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }
    }
}
