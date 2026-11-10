package com.kanhaji.singularity.feature.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kanhaji.core.navigation.navigator.LocalNavigator
import com.kanhaji.core.shell.state.rememberCollapsingTopBarState
import com.kanhaji.core.shell.ui.ScreenScaffold
import com.kanhaji.core.shell.ui.SmoothCollapsingTopBar
import com.kanhaji.core.shell.ui.rememberCollapsingTopBarConnection
import com.kanhaji.singularity.feature.audio.ui.AudioOutputBottomSheet
import com.kanhaji.singularity.feature.home.components.*
import com.kanhaji.singularity.feature.home.model.HomeFeatureCatalog
import com.kanhaji.singularity.navigation.AudioControlNavScreen
import com.kanhaji.singularity.navigation.AuthSettingsNavScreen
import com.kanhaji.singularity.navigation.CameraControlNavScreen
import com.kanhaji.singularity.navigation.FileBrowserNavScreen
import com.kanhaji.singularity.navigation.PowerControlsNavScreen
import com.kanhaji.singularity.navigation.SettingsNavScreen
import com.kanhaji.singularity.service.AppBridgeService
import com.kanhaji.singularity.service.bridge.AppBridgeState
import com.kanhaji.singularity.ui.components.AppSnackbar

@Composable
fun HomeScreenContent() {
    val pendingAuth by AppBridgeService.currentAuthRequest.collectAsState()
    val isPcConnected by AppBridgeService.isPcConnected.collectAsState()
    val pcLockState by AppBridgeService.pcLockState.collectAsState()
    val isPcLocked = pcLockState == "locked"
    val isLoggedIn by AppBridgeService.pcLoginState.collectAsState()
    val isCameraStreaming by AppBridgeService.isCameraStreaming.collectAsState()
    val audioSinks by AppBridgeState.availableLaptopAudioSinks.collectAsState()
    val selectedAudioRoute by AppBridgeState.selectedAudioRoute.collectAsState()
    val isListeningToLaptop by AppBridgeState.isListeningToLaptop.collectAsState()
    val activeAudioRoute by AppBridgeState.activeAudioRoute.collectAsState()
    var showAudioSheet by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val gridState = rememberLazyGridState()
    val collapsingState = rememberCollapsingTopBarState()
    val collapsingConnection = rememberCollapsingTopBarConnection(
        state = collapsingState,
        canExpand = { gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset == 0 }
    )

    DisposableEffect(Unit) {
        AppSnackbar.hasFab = true
        onDispose { AppSnackbar.hasFab = false }
    }

    ScreenScaffold(
        modifier = Modifier.nestedScroll(collapsingConnection),
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            SmoothCollapsingTopBar(
                title = "Singularity",
                showBack = false,
                onBack = {},
                actions = {
                    ConnectionStatusDot(isPcConnected = isPcConnected)
                    IconButton(onClick = { navigator.navigate(SettingsNavScreen) }) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                    }
                },
                state = collapsingState
            )
        },
        floatingActionButton = {
            HomeLockFab(
                isPcConnected = isPcConnected,
                isLoggedIn = isLoggedIn,
                isPcLocked = isPcLocked
            )
        }
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = gridState,
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 80.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (pendingAuth != null) {
                item(span = { GridItemSpan(2) }) {
                    HomePendingAuthCard(
                        auth = pendingAuth!!,
                        onVerify = { AppBridgeService.relaunchBiometricPrompt(context) },
                        onReject = { AppBridgeService.rejectActiveAuth("Rejected in app") }
                    )
                }
            }

            val isRoutingToLaptop = selectedAudioRoute != null && selectedAudioRoute != "phone"
            val hasActiveOutput = activeAudioRoute != null && activeAudioRoute!!.isPlaying
            if (isPcConnected && (isRoutingToLaptop || isListeningToLaptop || hasActiveOutput)) {
                item(span = { GridItemSpan(2) }) {
                    AudioOutputPill(selectedRoute = selectedAudioRoute, sinks = audioSinks, onClick = { showAudioSheet = true })
                }
            }

            item(span = { GridItemSpan(2) }) { FeatureSectionHeader("Features") }
            items(
                HomeFeatureCatalog.featureCards(
                    onOpenAuth = { navigator.navigate(AuthSettingsNavScreen) },
                    onOpenPower = { navigator.navigate(PowerControlsNavScreen) },
                    onOpenFiles = { navigator.navigate(FileBrowserNavScreen()) },
                    onOpenCamera = { navigator.navigate(CameraControlNavScreen) },
                    onOpenAudio = { navigator.navigate(AudioControlNavScreen) },
                    audioSubtitle = when {
                        isListeningToLaptop -> "Listening to PC sound"
                        isRoutingToLaptop -> audioSinks.find { it.name == selectedAudioRoute }?.nick ?: "Laptop audio"
                        else -> "Laptop speakers & displays"
                    },
                    isCameraActive = isCameraStreaming
                ),
                key = { it.id },
                span = { GridItemSpan(1) }
            ) { FeatureGridCard(it) }
        }
    }

    if (showAudioSheet) {
        AudioOutputBottomSheet(onDismiss = { showAudioSheet = false })
    }
}
