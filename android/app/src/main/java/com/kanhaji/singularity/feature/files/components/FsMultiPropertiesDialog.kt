package com.kanhaji.singularity.feature.files.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FolderCopy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kanhaji.singularity.feature.files.download.FileDownloadManager
import com.kanhaji.singularity.feature.files.model.FsSelectionState
import com.kanhaji.singularity.ui.components.AppSnackbar
import com.kanhaji.singularity.ui.components.OutlinedButton
import com.kanhaji.singularity.ui.components.SingularityDialog

@Composable
fun FsMultiPropertiesDialog(
    selectionState: FsSelectionState,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val stats = rememberMultiSelectionStats(selectionState)

    SingularityDialog(
        onDismissRequest = onDismiss,
        icon = {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .border(BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)), shape = RoundedCornerShape(12.dp))
            ) {
                Icon(Icons.Outlined.FolderCopy, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            }
        },
        title = { Text("${selectionState.count} Items Selected") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MultiPropertiesMetricPill("Size", stats.displaySize, Modifier.weight(1f))
                    MultiPropertiesMetricPill("Files", stats.displayFiles, Modifier.weight(1f))
                    MultiPropertiesMetricPill("Folders", stats.foldersCount, Modifier.weight(1f))
                }

                Text(
                    text = "Items in selection:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 160.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(selectionState.selectedItems.toList()) { item ->
                        val sizeText = if (item.is_dir) {
                            val prog = stats.folderProgress[item.path]
                            when {
                                prog?.isDone == true -> prog.formattedSize
                                prog?.bytes != null && prog.bytes > 0L -> prog.formattedSize
                                stats.isScanning -> "Calculating..."
                                else -> "Folder"
                            }
                        } else {
                            item.formattedSize
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.background,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                            ) {
                                Icon(
                                    imageVector = item.icon, contentDescription = null,
                                    tint = if (item.is_dir) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = item.name, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = sizeText,
                                    style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = {
                    FileDownloadManager.enqueue(context, selectionState.selectedItems.toList())
                    AppSnackbar.show("Downloading ${selectionState.count} item(s) in background...")
                    onDismiss()
                },
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
            ) {
                Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Save to Phone")
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
