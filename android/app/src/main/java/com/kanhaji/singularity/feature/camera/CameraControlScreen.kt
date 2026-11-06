package com.kanhaji.singularity.feature.camera

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.kanhaji.core.navigation.navigator.LocalNavigator
import com.kanhaji.core.shell.state.rememberCollapsingTopBarState
import com.kanhaji.core.shell.ui.ScreenScaffold
import com.kanhaji.core.shell.ui.SmoothCollapsingTopBar
import com.kanhaji.core.shell.ui.rememberCollapsingTopBarConnection
import com.kanhaji.singularity.feature.camera.components.*
import com.kanhaji.singularity.feature.home.components.FeatureSectionHeader
import com.kanhaji.singularity.service.AppBridgeService

@Composable
fun CameraControlScreen() {
    val isPcConnected by AppBridgeService.isPcConnected.collectAsState()
    val isStreaming by AppBridgeService.isCameraStreaming.collectAsState()
    val isMicActive by AppBridgeService.isCameraMicActive.collectAsState()
    val selectedLens by AppBridgeService.selectedCameraLens.collectAsState()
    val navigator = LocalNavigator.current
    val scrollState = rememberScrollState()
    val collapsingState = rememberCollapsingTopBarState()
    val collapsingConnection = rememberCollapsingTopBarConnection(
        state = collapsingState,
        canExpand = { scrollState.value == 0 }
    )

    val view = LocalView.current
    DisposableEffect(isStreaming) {
        view.keepScreenOn = isStreaming
        onDispose { view.keepScreenOn = false }
    }

    ScreenScaffold(
        modifier = Modifier.nestedScroll(collapsingConnection),
        containerColor = Color.Transparent,
        topBar = {
            SmoothCollapsingTopBar(
                title = "Continuity Camera",
                showBack = true,
                onBack = { navigator.pop() },
                actions = {},
                state = collapsingState
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(
                    top = innerPadding.calculateTopPadding() + 8.dp,
                    start = 16.dp,
                    end = 16.dp,
                    bottom = 32.dp
                ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CameraStatusCard(
                isPcConnected = isPcConnected,
                isStreaming = isStreaming,
                selectedLens = selectedLens,
                isMicActive = isMicActive
            )

            FeatureSectionHeader("Camera Sensor")

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LensOptionChip(
                    title = "Main (1x)",
                    subtitle = "Back Wide",
                    icon = Icons.Outlined.CameraRear,
                    isSelected = selectedLens == "main",
                    modifier = Modifier.weight(1f)
                ) {
                    if (isStreaming) AppBridgeService.requestSwitchLens("main")
                    else AppBridgeService.selectedCameraLens.value = "main"
                }

                LensOptionChip(
                    title = "Ultra (0.6x)",
                    subtitle = "Ultra Wide",
                    icon = Icons.Outlined.ZoomOut,
                    isSelected = selectedLens == "ultrawide",
                    modifier = Modifier.weight(1f)
                ) {
                    if (isStreaming) AppBridgeService.requestSwitchLens("ultrawide")
                    else AppBridgeService.selectedCameraLens.value = "ultrawide"
                }

                LensOptionChip(
                    title = "Front",
                    subtitle = "Selfie",
                    icon = Icons.Outlined.CameraFront,
                    isSelected = selectedLens == "front",
                    modifier = Modifier.weight(1f)
                ) {
                    if (isStreaming) AppBridgeService.requestSwitchLens("front")
                    else AppBridgeService.selectedCameraLens.value = "front"
                }
            }

            FeatureSectionHeader("Stream Capabilities")

            StreamCapabilitiesCard()

            Spacer(modifier = Modifier.height(6.dp))

            CameraStreamControls(
                isPcConnected = isPcConnected,
                isStreaming = isStreaming,
                onToggleStreaming = {
                    if (isStreaming) AppBridgeService.requestStopCamera()
                    else AppBridgeService.requestStartCamera(selectedLens)
                }
            )
        }
    }
}
