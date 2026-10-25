package com.kanhaji.singularity.feature.files.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kanhaji.singularity.feature.files.data.ArchiveRepository
import com.kanhaji.singularity.feature.files.data.FsRefreshManager
import com.kanhaji.singularity.feature.files.data.FsSelectionManager
import com.kanhaji.singularity.feature.files.model.FsItem
import com.kanhaji.singularity.ui.components.AppSnackbar
import com.kanhaji.singularity.ui.components.SingularityConfirmDialog
import com.kanhaji.core.designsystem.feedback.rememberHapticClick
import kotlinx.coroutines.launch

@Composable
fun FsCompressDialog(
    items: List<FsItem>,
    currentPath: String,
    onDismiss: () -> Unit
) {
    val formats = listOf(".zip", ".7z", ".tar.gz")
    var selectedFormat by remember { mutableStateOf(formats[0]) }
    val initialName = remember(items) {
        if (items.size == 1) items[0].name.substringBeforeLast('.') else "archive"
    }
    var archiveName by remember { mutableStateOf(initialName) }
    var isCompressing by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val trimmed = archiveName.trim()
    val hasSlash = archiveName.contains('/')
    val isValid = trimmed.isNotEmpty() && !hasSlash && items.isNotEmpty()

    SingularityConfirmDialog(
        title = "Compress to Archive",
        icon = Icons.Outlined.FolderZip,
        confirmText = "Compress",
        confirmEnabled = isValid,
        isLoading = isCompressing,
        onDismissRequest = onDismiss,
        onConfirm = {
            isCompressing = true
            coroutineScope.launch {
                val fullArchiveName = "$trimmed$selectedFormat"
                val targetPath = if (currentPath == "/") "/$fullArchiveName" else "$currentPath/$fullArchiveName"
                ArchiveRepository.compressToArchive(items.map { it.path }, targetPath)
                    .onSuccess {
                        AppSnackbar.show("Created $fullArchiveName")
                        FsRefreshManager.triggerRefresh()
                        FsSelectionManager.clear()
                        onDismiss()
                    }
                    .onFailure {
                        isCompressing = false
                        errorMsg = it.message ?: "Compression failed"
                    }
            }
        }
    ) {
        OutlinedTextField(
            value = archiveName,
            onValueChange = {
                archiveName = it
                errorMsg = null
            },
            label = { Text("Archive Name") },
            suffix = { Text(selectedFormat, color = MaterialTheme.colorScheme.primary) },
            singleLine = true,
            isError = hasSlash || errorMsg != null,
            supportingText = {
                if (hasSlash) {
                    Text("Name cannot contain '/'")
                } else if (errorMsg != null) {
                    Text(errorMsg ?: "")
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))
        Text("Format", style = MaterialTheme.typography.labelMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            formats.forEach { ext ->
                val isSelected = selectedFormat == ext
                FilterChip(
                    selected = isSelected,
                    onClick = rememberHapticClick { selectedFormat = ext },
                    label = { Text(ext) }
                )
            }
        }
    }
}
