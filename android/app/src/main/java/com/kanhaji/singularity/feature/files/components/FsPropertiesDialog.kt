package com.kanhaji.singularity.feature.files.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kanhaji.singularity.feature.files.data.FileBrowserRepository
import com.kanhaji.singularity.feature.files.download.FileDownloadManager
import com.kanhaji.singularity.feature.files.model.FsFolderSizeProgress
import com.kanhaji.singularity.feature.files.model.FsItem
import com.kanhaji.singularity.ui.components.AppSnackbar
import com.kanhaji.singularity.ui.components.OutlinedButton
import com.kanhaji.singularity.ui.components.SingularityDialog

@Composable
fun FsPropertiesDialog(
    item: FsItem,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var sizeProgress by remember(item.path) { mutableStateOf<FsFolderSizeProgress?>(null) }

    LaunchedEffect(item.path) {
        if (item.is_dir) {
            FileBrowserRepository.streamFolderSize(item.path).collect { sizeProgress = it }
        }
    }

    SingularityDialog(
        onDismissRequest = onDismiss,
        icon = {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .border(BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)), shape = RoundedCornerShape(12.dp))
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        title = {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PropertyField("Location", item.path)
                PropertyField("Type", if (item.is_dir) "Directory" else item.mime_type.ifEmpty { "File" })
                if (!item.is_dir) {
                    PropertyField("Size", "${item.formattedSize} (${item.size} bytes)")
                } else {
                    val progress = sizeProgress
                    val sizeText = when {
                        progress == null -> "Calculating..."
                        progress.error != null -> "Unknown (${progress.error})"
                        progress.isDone -> "${progress.formattedSize} (${progress.filesCount} files, ${progress.foldersCount} folders)"
                        else -> "${progress.formattedSize} (${progress.filesCount} files...)"
                    }
                    val isScanning = progress?.isDone != true && progress?.error == null
                    FolderSizePropertyField("Size", sizeText, isScanning = isScanning)
                }
                if (item.formattedModified.isNotEmpty()) {
                    PropertyField("Modified", item.formattedModified)
                }
                if (item.is_symlink) {
                    PropertyField("Attributes", "Symbolic Link")
                }
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = {
                    FileDownloadManager.enqueue(context, listOf(item))
                    val label = if (item.is_dir) "folder" else "file"
                    AppSnackbar.show("Downloading $label ${item.name} in background...")
                    onDismiss()
                },
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
            ) {
                Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (item.is_dir) "Save Folder" else "Save to Phone")
            }
        },
        confirmButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Close")
            }
        }
    )
}
