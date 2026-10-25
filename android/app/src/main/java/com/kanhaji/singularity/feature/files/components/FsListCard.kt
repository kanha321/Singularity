package com.kanhaji.singularity.feature.files.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kanhaji.singularity.feature.files.model.FsItem
import com.kanhaji.singularity.ui.feedback.rememberHapticClick
import com.kanhaji.singularity.ui.feedback.rememberHapticLongClick

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FsListCard(
    item: FsItem,
    isEnabled: Boolean = true,
    isSelected: Boolean = false,
    isInSelectionMode: Boolean = false,
    hapticFeedback: Boolean = true,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onMoreClick: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(16.dp)
    val primaryColor = MaterialTheme.colorScheme.primary
    val clickHandler = rememberHapticClick(enabled = hapticFeedback, onClick = onClick)
    val longClickHandler = rememberHapticLongClick(enabled = hapticFeedback, onLongClick = onLongClick)

    val targetContainerColor = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        isEnabled -> MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.25f)
    }
    val targetBorderColor = if (isSelected) primaryColor else primaryColor.copy(alpha = 0.25f)
    val targetBorderWidth = if (isSelected) 2.dp else 1.dp

    val containerColor by animateColorAsState(
        targetValue = targetContainerColor,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy),
        label = "cardContainerColor"
    )
    val borderColor by animateColorAsState(
        targetValue = targetBorderColor,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy),
        label = "cardBorderColor"
    )
    val borderWidth by animateDpAsState(
        targetValue = targetBorderWidth,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy),
        label = "cardBorderWidth"
    )

    Surface(
        shape = shape,
        color = containerColor,
        border = BorderStroke(borderWidth, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .combinedClickable(
                enabled = isEnabled,
                onClick = clickHandler,
                onLongClick = longClickHandler
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FsItemThumbnail(item = item, primaryColor = primaryColor)

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                val subtitle = if (item.is_dir) {
                    "Folder"
                } else {
                    val mod = item.formattedModified
                    if (mod.isNotEmpty()) "${item.formattedSize} • $mod" else item.formattedSize
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            FsCardTrailingIndicator(
                isInSelectionMode = isInSelectionMode,
                isSelected = isSelected,
                isDir = item.is_dir,
                onMoreClick = onMoreClick
            )
        }
    }
}
