package com.kanhaji.singularity.feature.files.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Icon
import com.kanhaji.singularity.ui.components.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun FsCardTrailingIndicator(
    isInSelectionMode: Boolean,
    isSelected: Boolean,
    isDir: Boolean,
    onMoreClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary

    AnimatedContent(
        targetState = when {
            isInSelectionMode -> if (isSelected) 1 else 0
            onMoreClick != null -> 2
            isDir -> 3
            else -> 4
        },
        transitionSpec = {
            (fadeIn(animationSpec = tween(150)) + scaleIn(initialScale = 0.8f, animationSpec = tween(150)))
                .togetherWith(fadeOut(animationSpec = tween(150)) + scaleOut(targetScale = 0.8f, animationSpec = tween(150)))
        },
        label = "indicatorTransition",
        modifier = modifier
    ) { state ->
        when (state) {
            1 -> Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Selected",
                tint = primaryColor,
                modifier = Modifier.size(22.dp)
            )
            0 -> Icon(
                imageVector = Icons.Outlined.RadioButtonUnchecked,
                contentDescription = "Not selected",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(22.dp)
            )
            2 -> IconButton(
                onClick = { onMoreClick?.invoke() },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.MoreVert,
                    contentDescription = "More Options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                )
            }
            3 -> Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(14.dp)
            )
            else -> Box(modifier = Modifier.size(14.dp))
        }
    }
}
