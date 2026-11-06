package com.kanhaji.singularity.feature.home.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kanhaji.core.designsystem.cards.SurfaceCard
import com.kanhaji.singularity.feature.home.model.FeatureCardModel

@Composable
fun FeatureGridCard(
    item: FeatureCardModel,
    modifier: Modifier = Modifier
) {
    val isAlert = item.isAlert
    val cardColor = if (isAlert) MaterialTheme.colorScheme.error else Color.Transparent
    val borderColor = when {
        isAlert -> MaterialTheme.colorScheme.error
        item.isHighlighted -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    }

    val iconTint = when {
        isAlert -> MaterialTheme.colorScheme.onError
        item.isHighlighted -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }

    val iconBoxBorder = when {
        isAlert -> MaterialTheme.colorScheme.onError.copy(alpha = 0.5f)
        item.isHighlighted -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    }

    val titleColor = if (isAlert) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurface
    val subtitleColor = if (isAlert) MaterialTheme.colorScheme.onError.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant

    SurfaceCard(
        onClick = item.onClick,
        cornerRadius = 20.dp,
        elevation = 0.dp,
        color = cardColor,
        borderWidth = 1.dp,
        borderColor = borderColor,
        modifier = modifier.height(130.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .then(
                            if (isAlert) Modifier.background(MaterialTheme.colorScheme.onError.copy(alpha = 0.15f))
                            else Modifier
                        )
                        .border(
                            BorderStroke(1.dp, iconBoxBorder),
                            shape = RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.title,
                        modifier = Modifier.size(20.dp),
                        tint = iconTint
                    )
                }

                if (item.statusText.isNotBlank()) {
                    FeatureStatusPill(
                        status = item.statusText,
                        isHighlighted = item.isHighlighted,
                        isAlert = isAlert
                    )
                }
            }

            Column {
                Text(
                    text = item.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = titleColor
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.subtitle,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = subtitleColor
                )
            }
        }
    }
}
