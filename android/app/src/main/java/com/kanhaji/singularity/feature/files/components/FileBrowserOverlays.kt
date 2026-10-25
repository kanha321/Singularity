package com.kanhaji.singularity.feature.files.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import com.kanhaji.core.navigation.navigator.AppNavigator
import com.kanhaji.singularity.feature.files.data.FsClipboardManager
import com.kanhaji.singularity.feature.files.data.FsDialogManager
import com.kanhaji.singularity.feature.files.data.FsSettingsManager
import com.kanhaji.singularity.feature.files.download.FileDownloadManager
import com.kanhaji.singularity.feature.files.model.FsItem
import com.kanhaji.singularity.feature.files.model.FsSelectionState
import com.kanhaji.singularity.feature.files.model.FsViewOptions
import com.kanhaji.singularity.feature.files.util.navigateToBreadcrumb
import com.kanhaji.singularity.ui.components.AppSnackbar

import com.kanhaji.singularity.feature.files.FolderNavScreen

@Composable
fun FileBrowserOverlays(
    currentPath: String,
    fileNavigator: AppNavigator,
    viewOptions: FsViewOptions,
    selectionState: FsSelectionState,
    showPlaces: Boolean,
    onDismissPlaces: () -> Unit,
    showOptions: Boolean,
    onDismissOptions: () -> Unit,
    singleInfoItem: FsItem?,
    onDismissInfo: () -> Unit,
    showMultiSummary: Boolean,
    onDismissMulti: () -> Unit,
    onShowProperties: (FsItem) -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val contextMenuItem by FsDialogManager.contextMenuItem.collectAsState()
    val renameItem by FsDialogManager.renameItem.collectAsState()
    val deleteItems by FsDialogManager.deleteItems.collectAsState()
    val showNewFolder by FsDialogManager.showNewFolder.collectAsState()
    val compressItems by FsDialogManager.compressItems.collectAsState()
    val extractArchiveItem by FsDialogManager.extractArchiveItem.collectAsState()

    contextMenuItem?.let { item ->
        FsItemContextMenuSheet(
            item = item,
            onSaveToPhone = {
                FileDownloadManager.enqueue(context, listOf(item))
                AppSnackbar.show("Downloading ${item.name}...")
                FsDialogManager.closeContextMenu()
            },
            onCut = {
                FsClipboardManager.cut(listOf(item))
                AppSnackbar.show("Cut ${item.name} to clipboard")
                FsDialogManager.closeContextMenu()
            },
            onCopy = {
                FsClipboardManager.copy(listOf(item))
                AppSnackbar.show("Copied ${item.name} to clipboard")
                FsDialogManager.closeContextMenu()
            },
            onRename = {
                FsDialogManager.closeContextMenu(); FsDialogManager.openRename(item)
            },
            onDelete = {
                FsDialogManager.closeContextMenu(); FsDialogManager.openDelete(listOf(item))
            },
            onCopyPath = {
                clipboardManager.setText(AnnotatedString(item.path))
                AppSnackbar.show("Path copied: ${item.path}")
                FsDialogManager.closeContextMenu()
            },
            onShowProperties = {
                FsDialogManager.closeContextMenu(); onShowProperties(item)
            },
            onExtract = {
                FsDialogManager.closeContextMenu(); FsDialogManager.openExtract(item)
            },
            onCompress = {
                FsDialogManager.closeContextMenu(); FsDialogManager.openCompress(listOf(item))
            },
            onBrowseArchive = {
                FsDialogManager.closeContextMenu()
                fileNavigator.navigate(FolderNavScreen("${item.path}!/"))
            },
            onDismiss = { FsDialogManager.closeContextMenu() }
        )
    }

    if (showNewFolder) {
        FsNewFolderDialog(currentPath = currentPath, onDismiss = { FsDialogManager.closeNewFolder() })
    }
    renameItem?.let { item ->
        FsRenameDialog(item = item, onDismiss = { FsDialogManager.closeRename() })
    }
    deleteItems?.let { items ->
        FsDeleteConfirmDialog(items = items, onDismiss = { FsDialogManager.closeDelete() })
    }
    compressItems?.let { items ->
        FsCompressDialog(items = items, currentPath = currentPath, onDismiss = { FsDialogManager.closeCompress() })
    }
    extractArchiveItem?.let { item ->
        FsExtractDialog(item = item, currentPath = currentPath, onDismiss = { FsDialogManager.closeExtract() })
    }

    if (showPlaces) {
        QuickPlacesBottomSheet(
            currentPath = currentPath,
            onDismissRequest = onDismissPlaces,
            onNavigate = { target -> navigateToBreadcrumb(fileNavigator, target) }
        )
    }
    if (showOptions) {
        FsViewOptionsSheet(
            options = viewOptions,
            onOptionsChange = { FsSettingsManager.setOptions(it) },
            onDismiss = onDismissOptions
        )
    }
    singleInfoItem?.let { FsPropertiesDialog(item = it, onDismiss = onDismissInfo) }
    if (showMultiSummary) {
        FsMultiPropertiesDialog(selectionState = selectionState, onDismiss = onDismissMulti)
    }
}
