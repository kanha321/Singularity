package com.kanhaji.singularity.feature.files.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kanhaji.singularity.feature.files.data.ArchiveRepository
import com.kanhaji.singularity.feature.files.data.FsRefreshManager
import com.kanhaji.singularity.feature.files.model.FsItem
import com.kanhaji.singularity.ui.components.AppSnackbar
import com.kanhaji.singularity.ui.components.SingularityConfirmDialog
import com.kanhaji.singularity.ui.feedback.hapticClickable
import kotlinx.coroutines.launch

@Composable
fun FsExtractDialog(
    item: FsItem,
    currentPath: String,
    onDismiss: () -> Unit
) {
    val subfolderName = remember(item.name) {
        val base = item.name.substringBeforeLast('.')
        if (base.lowercase().endsWith(".tar")) base.substringBeforeLast('.') else base
    }
    var extractToSubfolder by remember { mutableStateOf(true) }
    var isExtracting by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val targetDir = if (extractToSubfolder) {
        if (currentPath == "/") "/$subfolderName" else "$currentPath/$subfolderName"
    } else {
        currentPath
    }

    SingularityConfirmDialog(
        title = "Extract Archive",
        icon = Icons.Outlined.FolderZip,
        confirmText = "Extract",
        isLoading = isExtracting,
        onDismissRequest = onDismiss,
        onConfirm = {
            isExtracting = true
            coroutineScope.launch {
                ArchiveRepository.extractArchive(item.path, targetDir)
                    .onSuccess {
                        AppSnackbar.show("Extracted to ${if (extractToSubfolder) subfolderName else "current folder"}")
                        FsRefreshManager.triggerRefresh()
                        onDismiss()
                    }
                    .onFailure {
                        isExtracting = false
                        errorMsg = it.message ?: "Extraction failed"
                    }
            }
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Extracting ${item.name}", style = MaterialTheme.typography.bodyMedium)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .hapticClickable { extractToSubfolder = true }
                    .padding(vertical = 4.dp)
            ) {
                RadioButton(selected = extractToSubfolder, onClick = { extractToSubfolder = true })
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("Extract to folder", style = MaterialTheme.typography.bodyMedium)
                    Text(subfolderName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .hapticClickable { extractToSubfolder = false }
                    .padding(vertical = 4.dp)
            ) {
                RadioButton(selected = !extractToSubfolder, onClick = { extractToSubfolder = false })
                Spacer(modifier = Modifier.width(8.dp))
                Text("Extract here", style = MaterialTheme.typography.bodyMedium)
            }

            if (errorMsg != null) {
                Text(errorMsg ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
