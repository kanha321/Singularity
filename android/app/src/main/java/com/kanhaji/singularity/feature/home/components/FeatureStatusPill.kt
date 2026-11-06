package com.kanhaji.singularity.feature.home.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FeatureStatusPill(
    status: String,
    isHighlighted: Boolean,
    isAlert: Boolean = false,
    modifier: Modifier = Modifier
) {
    val tint = when {
        isAlert -> MaterialTheme.colorScheme.onError
        isHighlighted -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }

    val textColor = when {
        isAlert -> MaterialTheme.colorScheme.onError
        isHighlighted -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .clip(CircleShape)
            .then(
                if (isAlert) Modifier.background(MaterialTheme.colorScheme.onError.copy(alpha = 0.18f))
                else Modifier
            )
            .border(BorderStroke(1.dp, tint.copy(alpha = 0.50f)), shape = CircleShape)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = status,
            fontSize = 10.sp,
            fontWeight = if (isAlert) FontWeight.Bold else FontWeight.Normal,
            color = textColor
        )
    }
}
