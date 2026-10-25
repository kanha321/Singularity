package com.kanhaji.singularity.feature.files.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.kanhaji.singularity.feature.files.data.FileOperationsRepository
import com.kanhaji.singularity.feature.files.data.FsRefreshManager
import com.kanhaji.singularity.ui.components.AppSnackbar
import com.kanhaji.singularity.ui.components.SingularityConfirmDialog
import kotlinx.coroutines.launch

import com.kanhaji.singularity.feature.files.util.buildChildPath

@Composable
fun FsNewFolderDialog(
    currentPath: String,
    onDismiss: () -> Unit,
    onFolderCreated: () -> Unit = {}
) {
    var folderName by remember { mutableStateOf("") }
    var isCreating by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val trimmed = folderName.trim()
    val hasSlash = folderName.contains('/')
    val isValid = trimmed.isNotEmpty() && !hasSlash

    SingularityConfirmDialog(
        title = "New Folder",
        icon = Icons.Outlined.CreateNewFolder,
        confirmText = "Create",
        confirmEnabled = isValid,
        isLoading = isCreating,
        onDismissRequest = onDismiss,
        onConfirm = {
            isCreating = true
            coroutineScope.launch {
                val target = buildChildPath(currentPath, trimmed)
                FileOperationsRepository.createDirectory(target)
                    .onSuccess {
                        AppSnackbar.show("Folder '$trimmed' created")
                        FsRefreshManager.triggerRefresh()
                        onFolderCreated()
                        onDismiss()
                    }
                    .onFailure {
                        isCreating = false
                        errorMsg = it.message ?: "Failed to create folder"
                    }
            }
        }
    ) {
        OutlinedTextField(
            value = folderName,
            onValueChange = {
                folderName = it
                errorMsg = null
            },
            label = { Text("Folder Name") },
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
