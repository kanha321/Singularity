package com.kanhaji.singularity.feature.files.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.kanhaji.singularity.feature.files.data.FileOperationsRepository
import com.kanhaji.singularity.feature.files.data.FsClipboardManager
import com.kanhaji.singularity.feature.files.data.FsDialogManager
import com.kanhaji.singularity.feature.files.data.FsRefreshManager
import com.kanhaji.singularity.feature.files.data.FsSelectionManager
import com.kanhaji.singularity.feature.files.model.FsClipboardOp
import com.kanhaji.singularity.ui.components.AppSnackbar
import com.kanhaji.singularity.ui.feedback.rememberHapticClick
import kotlinx.coroutines.launch

@Composable
fun FsBrowserFab(
    currentPath: String,
    modifier: Modifier = Modifier,
    onNewFolder: () -> Unit = { FsDialogManager.openNewFolder() }
) {
    val selectionState by FsSelectionManager.state.collectAsState()
    val clipboardState by FsClipboardManager.state.collectAsState()
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    val isVisible = !selectionState.isActive
    val hasPaste = clipboardState.isActive

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(200)) + scaleIn(initialScale = 0.7f, animationSpec = tween(250)),
        exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.7f, animationSpec = tween(200)),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AnimatedVisibility(
                visible = hasPaste,
                enter = fadeIn(tween(200)) + scaleIn(initialScale = 0.7f, animationSpec = tween(200)),
                exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.7f, animationSpec = tween(150))
            ) {
                SmallFloatingActionButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        FsClipboardManager.clear()
                        AppSnackbar.show("Paste cancelled")
                    },
                    shape = RoundedCornerShape(12.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 3.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel Paste", modifier = Modifier.size(18.dp))
                }
            }

            val containerColor = if (hasPaste) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer
            val contentColor = if (hasPaste) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer

            FloatingActionButton(
                onClick = rememberHapticClick {
                    if (hasPaste) {
                        val sources = clipboardState.items.map { it.path }
                        val isCut = clipboardState.op == FsClipboardOp.CUT
                        scope.launch {
                            val result = if (isCut) FileOperationsRepository.move(sources, currentPath) else FileOperationsRepository.copy(sources, currentPath)
                            result.onSuccess { count ->
                                AppSnackbar.show(if (isCut) "Moved $count item(s)" else "Copied $count item(s)")
                                FsClipboardManager.clear()
                                FsRefreshManager.triggerRefresh()
                            }.onFailure { err ->
                                AppSnackbar.show("Paste failed: ${err.message}")
                            }
                        }
                    } else {
                        onNewFolder()
                    }
                },
                shape = RoundedCornerShape(16.dp),
                containerColor = containerColor,
                contentColor = contentColor,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp)
            ) {
                AnimatedContent(
                    targetState = hasPaste,
                    transitionSpec = {
                        (fadeIn(tween(200)) + scaleIn(initialScale = 0.6f, animationSpec = tween(200)))
                            .togetherWith(fadeOut(tween(150)) + scaleOut(targetScale = 0.6f, animationSpec = tween(150)))
                    },
                    label = "fabIconMorph"
                ) { isPaste ->
                    Icon(
                        imageVector = if (isPaste) Icons.Outlined.ContentPaste else Icons.Outlined.CreateNewFolder,
                        contentDescription = if (isPaste) "Paste Here" else "New Folder",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
