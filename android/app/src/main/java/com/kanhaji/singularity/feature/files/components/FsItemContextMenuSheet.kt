package com.kanhaji.singularity.feature.files.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kanhaji.singularity.feature.files.model.FsItem

import com.kanhaji.singularity.ui.feedback.hapticClickable

import com.kanhaji.singularity.feature.files.util.isArchive
import com.kanhaji.singularity.feature.files.util.isArchivePath

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FsItemContextMenuSheet(
    item: FsItem,
    onSaveToPhone: () -> Unit,
    onCut: () -> Unit,
    onCopy: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onCopyPath: () -> Unit,
    onShowProperties: () -> Unit,
    onExtract: (() -> Unit)? = null,
    onCompress: (() -> Unit)? = null,
    onBrowseArchive: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FsItemThumbnail(
                    item = item,
                    primaryColor = MaterialTheme.colorScheme.primary,
                    size = 48.dp,
                    iconSize = 26.dp
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (item.is_dir) "Directory" else item.formattedSize,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            val isInsideArchive = isArchivePath(item.path)
            val isArchiveFile = isArchive(item.name) && !isInsideArchive

            ContextMenuItem(
                icon = Icons.Outlined.Download,
                title = if (item.is_dir) "Save Folder to Phone" else "Save to Phone",
                onClick = onSaveToPhone
            )
            if (isArchiveFile && onBrowseArchive != null) {
                ContextMenuItem(icon = Icons.Outlined.FolderZip, title = "Browse Contents", onClick = onBrowseArchive)
            }
            if (isArchiveFile && onExtract != null) {
                ContextMenuItem(icon = Icons.Outlined.Unarchive, title = "Extract on Laptop", onClick = onExtract)
            }
            ContextMenuItem(icon = Icons.Outlined.ContentCut, title = "Cut", onClick = onCut)
            ContextMenuItem(icon = Icons.Outlined.ContentCopy, title = "Copy", onClick = onCopy)
            ContextMenuItem(icon = Icons.Outlined.DriveFileRenameOutline, title = "Rename", onClick = onRename)
            if (!isInsideArchive && onCompress != null) {
                ContextMenuItem(icon = Icons.Outlined.Archive, title = "Compress to Archive", onClick = onCompress)
            }
            ContextMenuItem(
                icon = Icons.Outlined.DeleteOutline,
                title = "Delete",
                titleColor = MaterialTheme.colorScheme.error,
                onClick = onDelete
            )
            ContextMenuItem(icon = Icons.Outlined.Link, title = "Copy Laptop Path", onClick = onCopyPath)
            ContextMenuItem(icon = Icons.Outlined.Info, title = "Properties", onClick = onShowProperties)
        }
    }
}

@Composable
private fun ContextMenuItem(
    icon: ImageVector,
    title: String,
    titleColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .hapticClickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (titleColor == MaterialTheme.colorScheme.onSurface) MaterialTheme.colorScheme.primary else titleColor,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = title, style = MaterialTheme.typography.bodyLarge, color = titleColor)
    }
}
