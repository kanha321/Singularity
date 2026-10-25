package com.kanhaji.singularity.feature.files.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.kanhaji.singularity.ui.components.IconButton
import com.kanhaji.singularity.ui.feedback.hapticClickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.kanhaji.singularity.feature.files.model.parsePathSegments

@Composable
fun FsBreadcrumbBar(
    currentPath: String,
    isEditing: Boolean,
    onEditingChange: (Boolean) -> Unit,
    onNavigateToPath: (String) -> Unit,
    modifier: Modifier = Modifier,
    isSudoActive: Boolean = false
) {
    var editText by remember(currentPath) { mutableStateOf(currentPath) }
    val segments = remember(currentPath, isSudoActive) { parsePathSegments(currentPath, isSudoActive) }
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(segments.size) {
        if (segments.isNotEmpty()) listState.animateScrollToItem(segments.size - 1)
    }

    LaunchedEffect(isEditing) {
        if (isEditing) {
            editText = currentPath
            focusRequester.requestFocus()
        }
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
        modifier = modifier.fillMaxWidth().height(48.dp)
    ) {
        if (isEditing) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (editText.isNotBlank()) onNavigateToPath(editText.trim())
                        onEditingChange(false)
                    }),
                    modifier = Modifier.weight(1f).focusRequester(focusRequester),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (editText.isEmpty()) {
                                Text(
                                    "Enter path...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                            inner()
                        }
                    }
                )
                IconButton(onClick = {
                    if (editText.isNotBlank()) onNavigateToPath(editText.trim())
                    onEditingChange(false)
                }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.Check, contentDescription = "Go", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = { onEditingChange(false) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.Close, contentDescription = "Cancel", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LazyRow(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    itemsIndexed(segments) { index, seg ->
                        val isLast = index == segments.size - 1
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isLast) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(1.dp, if (isLast) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).hapticClickable { onNavigateToPath(seg.fullPath) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (seg.isArchive) {
                                    Icon(Icons.Outlined.FolderZip, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                } else if (seg.isHome) {
                                    Icon(Icons.Outlined.Home, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                } else if (seg.isRoot) {
                                    val icon = if (isSudoActive) Icons.Outlined.Shield else Icons.Outlined.Storage
                                    val tint = if (isSudoActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                    Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = tint)
                                }
                                Text(seg.name, style = MaterialTheme.typography.labelMedium, color = if (isLast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                        if (!isLast) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, modifier = Modifier.size(9.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                        }
                    }
                }
                IconButton(onClick = { onEditingChange(true) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.Edit, contentDescription = "Edit Path", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
