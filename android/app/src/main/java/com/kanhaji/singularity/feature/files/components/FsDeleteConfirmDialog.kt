package com.kanhaji.singularity.feature.files.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import com.kanhaji.singularity.feature.files.data.FileOperationsRepository
import com.kanhaji.singularity.feature.files.data.FsRefreshManager
import com.kanhaji.singularity.feature.files.data.FsSelectionManager
import com.kanhaji.singularity.feature.files.model.FsItem
import com.kanhaji.singularity.ui.components.AppSnackbar
import com.kanhaji.singularity.ui.components.SingularityConfirmDialog
import kotlinx.coroutines.launch

@Composable
fun FsDeleteConfirmDialog(
    items: List<FsItem>,
    onDismiss: () -> Unit,
    onDeleted: () -> Unit = {}
) {
    if (items.isEmpty()) {
        onDismiss()
        return
    }

    var isDeleting by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val title = if (items.size == 1) "Delete ${if (items.first().is_dir) "Folder" else "File"}" else "Delete ${items.size} Items"
    val message = if (items.size == 1) {
        "Are you sure you want to delete '${items.first().name}'? This action cannot be undone."
    } else {
        "Are you sure you want to delete these ${items.size} items? This action cannot be undone."
    }

    SingularityConfirmDialog(
        title = title,
        icon = Icons.Outlined.DeleteOutline,
        message = message,
        confirmText = "Delete",
        isDestructive = true,
        isLoading = isDeleting,
        onDismissRequest = onDismiss,
        onConfirm = {
            isDeleting = true
            coroutineScope.launch {
                FileOperationsRepository.delete(items.map { it.path })
                    .onSuccess { count ->
                        AppSnackbar.show("Deleted $count item(s)")
                        FsRefreshManager.triggerRefresh()
                        FsSelectionManager.clear()
                        onDeleted()
                        onDismiss()
                    }
                    .onFailure {
                        isDeleting = false
                        errorMsg = it.message ?: "Failed to delete"
                    }
            }
        }
    ) {
        errorMsg?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        }
    }
}
