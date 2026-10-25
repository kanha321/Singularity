package com.kanhaji.singularity.feature.files.components

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.kanhaji.singularity.feature.files.data.FsClipboardManager
import com.kanhaji.singularity.feature.files.data.FsSelectionManager
import com.kanhaji.singularity.feature.files.download.FileDownloadManager
import com.kanhaji.singularity.feature.files.model.FsItem
import com.kanhaji.singularity.feature.files.model.FsSelectionState
import com.kanhaji.singularity.ui.components.AppSnackbar

@Composable
fun BoxScope.FileBrowserSelectionDock(
    selectionState: FsSelectionState,
    onShowInfo: () -> Unit,
    onDeleteSelected: ((List<FsItem>) -> Unit)? = null,
    onCompressSelected: ((List<FsItem>) -> Unit)? = null
) {
    val context = LocalContext.current

    FsSelectionDock(
        selectionState = selectionState,
        onShowInfo = onShowInfo,
        onSaveToPhone = {
            val items = selectionState.selectedItems.toList()
            if (items.isNotEmpty()) {
                FileDownloadManager.enqueue(context, items)
                AppSnackbar.show("Downloading ${items.size} item(s) in background...")
            }
            FsSelectionManager.clear()
        },
        onCut = {
            val items = selectionState.selectedItems.toList()
            FsClipboardManager.cut(items)
            AppSnackbar.show("${items.size} item(s) cut to clipboard")
            FsSelectionManager.clear()
        },
        onCopy = {
            val items = selectionState.selectedItems.toList()
            FsClipboardManager.copy(items)
            AppSnackbar.show("${items.size} item(s) copied to clipboard")
            FsSelectionManager.clear()
        },
        onDelete = {
            val items = selectionState.selectedItems.toList()
            onDeleteSelected?.invoke(items)
        },
        onCompress = if (onCompressSelected != null) {
            { onCompressSelected(selectionState.selectedItems.toList()) }
        } else null,
        onSelectAll = { FsSelectionManager.selectAll() },
        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
    )
}
