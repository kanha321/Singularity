package com.kanhaji.singularity.feature.files.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kanhaji.singularity.feature.files.model.FsSelectionState
import com.kanhaji.singularity.ui.components.IconButton

@Composable
fun FsSelectionDock(
    selectionState: FsSelectionState,
    onShowInfo: () -> Unit,
    onSaveToPhone: () -> Unit,
    onCut: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onCompress: (() -> Unit)? = null,
    onSelectAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = selectionState.isActive,
        enter = slideInVertically(
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioLowBouncy),
            initialOffsetY = { it * 2 }
        ) + fadeIn(animationSpec = tween(200)),
        exit = slideOutVertically(
            animationSpec = tween(180, easing = FastOutLinearInEasing),
            targetOffsetY = { it * 2 }
        ) + fadeOut(animationSpec = tween(150)),
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp,
            shadowElevation = 6.dp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier
                    .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                IconButton(onClick = onSaveToPhone) {
                    Icon(Icons.Outlined.Download, contentDescription = "Save to Phone")
                }
                IconButton(onClick = onCut) {
                    Icon(Icons.Outlined.ContentCut, contentDescription = "Cut")
                }
                IconButton(onClick = onCopy) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy")
                }
                if (onCompress != null) {
                    IconButton(onClick = onCompress) {
                        Icon(Icons.Outlined.FolderZip, contentDescription = "Compress")
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                }
                IconButton(onClick = onShowInfo) {
                    Icon(Icons.Outlined.Info, contentDescription = "Summary")
                }
                IconButton(onClick = onSelectAll) {
                    Icon(Icons.Outlined.SelectAll, contentDescription = "Select All")
                }
            }
        }
    }
}
