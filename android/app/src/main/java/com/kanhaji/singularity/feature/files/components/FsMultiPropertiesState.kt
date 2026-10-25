package com.kanhaji.singularity.feature.files.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kanhaji.singularity.feature.files.data.FileBrowserRepository
import com.kanhaji.singularity.feature.files.download.FileDownloadUtils
import com.kanhaji.singularity.feature.files.model.FsFolderSizeProgress
import com.kanhaji.singularity.feature.files.model.FsSelectionState
import kotlinx.coroutines.launch

data class MultiSelectionStats(
    val displaySize: String,
    val displayFiles: String,
    val foldersCount: String,
    val isScanning: Boolean,
    val folderProgress: Map<String, FsFolderSizeProgress>
)

@Composable
fun rememberMultiSelectionStats(selectionState: FsSelectionState): MultiSelectionStats {
    val selectedFolders = remember(selectionState) { selectionState.selectedItems.filter { it.is_dir } }
    val selectedFiles = remember(selectionState) { selectionState.selectedItems.filter { !it.is_dir } }
    val folderProgressMap = remember(selectionState) { mutableStateMapOf<String, FsFolderSizeProgress>() }

    LaunchedEffect(selectionState) {
        selectedFolders.forEach { folder ->
            launch {
                FileBrowserRepository.streamFolderSize(folder.path).collect {
                    folderProgressMap[folder.path] = it
                }
            }
        }
    }

    val directFilesBytes = remember(selectedFiles) { selectedFiles.sumOf { it.size } }
    val scannedFolderBytes = folderProgressMap.values.sumOf { it.bytes }
    val totalBytes = directFilesBytes + scannedFolderBytes

    val directFilesCount = selectedFiles.size.toLong()
    val scannedFilesCount = folderProgressMap.values.sumOf { it.filesCount }
    val totalFiles = directFilesCount + scannedFilesCount

    val isScanning = selectedFolders.any { folder ->
        val prog = folderProgressMap[folder.path]
        prog == null || (!prog.isDone && prog.error == null)
    }

    val displaySize = when {
        isScanning && totalBytes == 0L -> "Calculating..."
        else -> FileDownloadUtils.formatBytes(totalBytes)
    }

    val displayFiles = when {
        isScanning && totalFiles == 0L -> "..."
        else -> "$totalFiles"
    }

    return MultiSelectionStats(
        displaySize = displaySize,
        displayFiles = displayFiles,
        foldersCount = "${selectionState.foldersCount}",
        isScanning = isScanning,
        folderProgress = folderProgressMap
    )
}

@Composable
fun MultiPropertiesMetricPill(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.background,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
