package com.kanhaji.singularity.feature.auth.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kanhaji.core.designsystem.cards.SurfaceCard
import com.kanhaji.singularity.feature.auth.model.AuthLogItem
import com.kanhaji.singularity.feature.home.components.FeatureStatusPill

@Composable
fun AuthLogSection(
    logs: List<AuthLogItem>,
    modifier: Modifier = Modifier
) {
    if (logs.isEmpty()) {
        SurfaceCard(
            color = Color.Transparent,
            borderWidth = 1.dp,
            borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
            cornerRadius = 16.dp,
            elevation = 0.dp,
            modifier = modifier.fillMaxWidth()
        ) {
            Box(modifier = Modifier.padding(16.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = "No recent authentication requests",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        logs.take(5).forEach { item ->
            SurfaceCard(
                color = Color.Transparent,
                borderWidth = 1.dp,
                borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                cornerRadius = 14.dp,
                elevation = 0.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(item.action, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text(
                            "${item.mode} • ${item.formattedTime}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    FeatureStatusPill(
                        status = item.result,
                        isHighlighted = item.isSuccess
                    )
                }
            }
        }
    }
}
