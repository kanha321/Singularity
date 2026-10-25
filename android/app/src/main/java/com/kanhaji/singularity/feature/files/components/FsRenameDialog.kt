package com.kanhaji.singularity.feature.files.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.kanhaji.singularity.feature.files.data.FileOperationsRepository
import com.kanhaji.singularity.feature.files.data.FsRefreshManager
import com.kanhaji.singularity.feature.files.model.FsItem
import com.kanhaji.singularity.ui.components.AppSnackbar
import com.kanhaji.singularity.ui.components.SingularityConfirmDialog
import kotlinx.coroutines.launch

import com.kanhaji.singularity.feature.files.util.buildChildPath

@Composable
fun FsRenameDialog(
    item: FsItem,
    onDismiss: () -> Unit,
    onRenamed: () -> Unit = {}
) {
    var newName by remember(item) { mutableStateOf(item.name) }
    var isRenaming by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val trimmed = newName.trim()
    val hasSlash = newName.contains('/')
    val isValid = trimmed.isNotEmpty() && !hasSlash && trimmed != item.name

    SingularityConfirmDialog(
        title = "Rename ${if (item.is_dir) "Folder" else "File"}",
        icon = Icons.Outlined.DriveFileRenameOutline,
        confirmText = "Rename",
        confirmEnabled = isValid,
        isLoading = isRenaming,
        onDismissRequest = onDismiss,
        onConfirm = {
            isRenaming = true
            coroutineScope.launch {
                val parent = item.path.substringBeforeLast('/', "")
                val target = buildChildPath(parent, trimmed)
                FileOperationsRepository.rename(item.path, target)
                    .onSuccess {
                        AppSnackbar.show("Renamed to '$trimmed'")
                        FsRefreshManager.triggerRefresh()
                        onRenamed()
                        onDismiss()
                    }
                    .onFailure {
                        isRenaming = false
                        errorMsg = it.message ?: "Failed to rename"
                    }
            }
        }
    ) {
        OutlinedTextField(
            value = newName,
            onValueChange = {
                newName = it
                errorMsg = null
            },
            label = { Text("New Name") },
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
    }
}
