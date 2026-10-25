package com.kanhaji.singularity.feature.files

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kanhaji.core.navigation.display.ScreenNavDisplay
import com.kanhaji.core.navigation.navigator.AppNavigator
import com.kanhaji.core.navigation.navigator.LocalNavigator
import com.kanhaji.core.shell.state.rememberCollapsingTopBarState
import com.kanhaji.core.shell.ui.ScreenScaffold
import com.kanhaji.core.shell.ui.SmoothCollapsingTopBar
import com.kanhaji.core.shell.ui.rememberCollapsingTopBarConnection
import com.kanhaji.singularity.feature.files.components.*
import com.kanhaji.singularity.feature.files.data.*
import com.kanhaji.singularity.feature.files.model.FsItem
import com.kanhaji.singularity.feature.files.security.FsBiometricHelper
import com.kanhaji.singularity.feature.files.security.FsSudoSessionManager
import com.kanhaji.singularity.feature.files.util.formatFolderTitle
import com.kanhaji.singularity.feature.files.util.navigateToBreadcrumb
import com.kanhaji.singularity.navigation.LocalRootNavigator
import com.kanhaji.singularity.ui.components.AppSnackbar

@Composable
fun FileBrowserScreen(initialPath: String = "~") {
    val rootNavigator = LocalNavigator.current
    val fileNavigator = remember(initialPath) { AppNavigator(FolderNavScreen(initialPath)) }
    val collapsingState = rememberCollapsingTopBarState()
    val collapsingConnection = rememberCollapsingTopBarConnection(state = collapsingState)
    var isEditingPath by remember { mutableStateOf(false) }
    var showOptionsSheet by remember { mutableStateOf(false) }
    var showPlacesSheet by remember { mutableStateOf(false) }
    var singleInfoItem by remember { mutableStateOf<FsItem?>(null) }
    var showMultiSummary by remember { mutableStateOf(false) }

    val selectionState by FsSelectionManager.state.collectAsState()
    val clipboardState by FsClipboardManager.state.collectAsState()
    val viewOptions by FsSettingsManager.viewOptions.collectAsState()
    val sudoState by FsSudoSessionManager.state.collectAsState()
    val context = LocalContext.current

    val currentPath = (fileNavigator.currentRoute as? FolderNavScreen)?.path ?: initialPath
    val folderTitle = formatFolderTitle(currentPath)
    val isSudoElevated = sudoState.isElevated

    DisposableEffect(Unit) { onDispose { FsSudoSessionManager.dropPrivileges() } }
    BackHandler(enabled = clipboardState.isActive && !selectionState.isActive && !isEditingPath) {
        FsClipboardManager.clear(); AppSnackbar.show("Paste cancelled")
    }
    BackHandler(enabled = selectionState.isActive) { FsSelectionManager.clear() }
    BackHandler(enabled = isEditingPath && !selectionState.isActive) { isEditingPath = false }

    ScreenScaffold(
        modifier = Modifier.nestedScroll(collapsingConnection),
        containerColor = Color.Transparent,
        topBar = {
            SmoothCollapsingTopBar(
                title = if (selectionState.isActive) "${selectionState.count} selected" else folderTitle,
                showBack = true,
                onBack = {
                    when {
                        selectionState.isActive -> FsSelectionManager.clear()
                        clipboardState.isActive -> { FsClipboardManager.clear(); AppSnackbar.show("Paste cancelled") }
                        else -> rootNavigator.pop()
                    }
                },
                actions = {
                    if (!selectionState.isActive) {
                        FileBrowserTopBarActions(
                            isSudoElevated = isSudoElevated,
                            onToggleSudo = {
                                if (isSudoElevated) {
                                    FsSudoSessionManager.dropPrivileges(); AppSnackbar.show("Sudo privileges dropped")
                                } else {
                                    FsBiometricHelper.promptSudoElevation(context = context, onSuccess = {})
                                }
                            },
                            onOpenPlaces = { showPlacesSheet = true },
                            onOpenOptions = { showOptionsSheet = true }
                        )
                    }
                },
                state = collapsingState
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding(), bottom = innerPadding.calculateBottomPadding())
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                FsBreadcrumbBar(
                    currentPath = currentPath,
                    isEditing = isEditingPath,
                    onEditingChange = { isEditingPath = it },
                    onNavigateToPath = { target ->
                        navigateToBreadcrumb(fileNavigator, target) { FsBiometricHelper.promptSudoElevation(context, onSuccess = it) }
                    },
                    isSudoActive = isSudoElevated,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
                CompositionLocalProvider(
                    LocalRootNavigator provides rootNavigator,
                    LocalNavigator provides fileNavigator
                ) {
                    ScreenNavDisplay(navigator = fileNavigator, modifier = Modifier.weight(1f).fillMaxWidth())
                }
            }

            if (isEditingPath) {
                Box(modifier = Modifier.fillMaxSize().padding(top = 58.dp).clickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null
                ) { isEditingPath = false })
            }

            FsBrowserFab(
                currentPath = currentPath,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).navigationBarsPadding()
            )

            FileBrowserSelectionDock(
                selectionState = selectionState,
                onShowInfo = { if (selectionState.count == 1) singleInfoItem = selectionState.selectedItems.first() else showMultiSummary = true },
                onDeleteSelected = { FsDialogManager.openDelete(it) },
                onCompressSelected = { FsDialogManager.openCompress(it) }
            )
        }
    }

    FileBrowserOverlays(
        currentPath = currentPath, fileNavigator = fileNavigator, viewOptions = viewOptions,
        selectionState = selectionState, showPlaces = showPlacesSheet, onDismissPlaces = { showPlacesSheet = false },
        showOptions = showOptionsSheet, onDismissOptions = { showOptionsSheet = false },
        singleInfoItem = singleInfoItem, onDismissInfo = { singleInfoItem = null },
        showMultiSummary = showMultiSummary, onDismissMulti = { showMultiSummary = false },
        onShowProperties = { singleInfoItem = it }
    )
}
