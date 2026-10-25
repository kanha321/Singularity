package com.kanhaji.singularity.feature.files

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kanhaji.singularity.feature.files.components.FsListCard
import com.kanhaji.singularity.feature.files.data.ArchiveRepository
import com.kanhaji.singularity.feature.files.data.FileBrowserRepository
import com.kanhaji.singularity.feature.files.data.FsRefreshManager
import com.kanhaji.singularity.feature.files.data.FsScrollStateManager
import com.kanhaji.singularity.feature.files.data.FsSelectionManager
import com.kanhaji.singularity.feature.files.data.FsSettingsManager
import com.kanhaji.singularity.feature.files.model.FsItem
import com.kanhaji.singularity.feature.files.model.applyOptions
import com.kanhaji.singularity.feature.files.util.isArchive
import com.kanhaji.singularity.feature.files.util.isArchivePath
import com.kanhaji.singularity.feature.files.util.splitArchivePath
import com.kanhaji.singularity.service.AppBridgeService
import com.kanhaji.singularity.ui.components.AppSnackbar

@Composable
fun FolderContentList(
    path: String,
    onFolderClick: (String) -> Unit,
    onFileClick: (FsItem) -> Unit,
    onItemMoreClick: ((FsItem) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isPcConnected by AppBridgeService.isPcConnected.collectAsState()
    val viewOptions by FsSettingsManager.viewOptions.collectAsState()
    val selectionState by FsSelectionManager.state.collectAsState()
    val refreshTrigger by FsRefreshManager.refreshTrigger.collectAsState()
    val cached = remember(path, refreshTrigger) {
        if (isArchivePath(path)) null else FileBrowserRepository.getCached(path)
    }
    var rawItems by remember(path, refreshTrigger) { mutableStateOf(cached ?: emptyList()) }
    var isLoading by remember(path, refreshTrigger) { mutableStateOf(cached == null) }
    val listState = rememberLazyListState()

    val displayedItems = remember(rawItems, viewOptions) {
        rawItems.applyOptions(viewOptions)
    }

    LaunchedEffect(displayedItems) {
        FsSelectionManager.setCurrentItems(displayedItems)
    }

    LaunchedEffect(path, isPcConnected, refreshTrigger) {
        if (!isPcConnected) {
            isLoading = false
            return@LaunchedEffect
        }
        val result = if (isArchivePath(path)) {
            val (archive, internal) = splitArchivePath(path)
            ArchiveRepository.listArchive(archive, internal)
        } else {
            FileBrowserRepository.listDirectory(path)
        }
        result.onSuccess {
            rawItems = it
            isLoading = false
        }.onFailure {
            isLoading = false
            AppSnackbar.show("Failed to read: ${it.localizedMessage}")
        }
    }

    LaunchedEffect(path, displayedItems.isNotEmpty()) {
        if (displayedItems.isNotEmpty()) {
            FsScrollStateManager.getPosition(path)?.let { (idx, offset) ->
                if (idx < displayedItems.size) {
                    listState.scrollToItem(idx, offset)
                }
            }
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { Pair(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) }
            .collect { (idx, offset) ->
                if (idx > 0 || offset > 0) {
                    FsScrollStateManager.savePosition(path, idx, offset)
                }
            }
    }

    if (isLoading && displayedItems.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
    } else if (displayedItems.isEmpty()) {
        Box(modifier = modifier.fillMaxSize().padding(top = 40.dp), contentAlignment = Alignment.TopCenter) {
            Text(
                text = if (rawItems.isNotEmpty()) "Hidden files filtered out" else "No files in this directory",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(items = displayedItems, key = { it.path }) { item ->
                FsListCard(
                    item = item,
                    isEnabled = isPcConnected,
                    isSelected = selectionState.isSelected(item),
                    isInSelectionMode = selectionState.isActive,
                    onClick = {
                        if (selectionState.isActive) {
                            FsSelectionManager.toggle(item)
                        } else if (item.is_dir) {
                            onFolderClick(item.path)
                        } else if (isArchive(item.name) && !isArchivePath(item.path)) {
                            onFolderClick("${item.path}!/")
                        } else {
                            onFileClick(item)
                        }
                    },
                    onLongClick = {
                        FsSelectionManager.startSelection(item)
                    },
                    onMoreClick = onItemMoreClick?.let { { it(item) } }
                )
            }
        }
    }
}
