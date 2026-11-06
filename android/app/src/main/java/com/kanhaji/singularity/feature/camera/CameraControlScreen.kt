package com.kanhaji.singularity.feature.camera

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kanhaji.core.navigation.navigator.LocalNavigator
import com.kanhaji.core.shell.state.rememberCollapsingTopBarState
import com.kanhaji.core.shell.ui.ScreenScaffold
import com.kanhaji.core.shell.ui.SmoothCollapsingTopBar
import com.kanhaji.singularity.feature.camera.components.*
import com.kanhaji.singularity.service.AppBridgeService

@Composable
fun CameraControlScreen() {
    val isPcConnected by AppBridgeService.isPcConnected.collectAsState()
    val isStreaming by AppBridgeService.isCameraStreaming.collectAsState()
    val selectedLens by AppBridgeService.selectedCameraLens.collectAsState()
    val navigator = LocalNavigator.current
    val collapsingState = rememberCollapsingTopBarState()

    val view = LocalView.current
    DisposableEffect(isStreaming) {
        view.keepScreenOn = isStreaming
        onDispose { view.keepScreenOn = false }
    }

    ScreenScaffold(
        containerColor = MaterialTheme.colorScheme.background,
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
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CameraStatusCard(
                isPcConnected = isPcConnected,
                isStreaming = isStreaming,
                selectedLens = selectedLens
            )

            Text(
                text = "Camera Sensor",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LensOptionChip(
                    title = "Main (1x)",
                    subtitle = "Back",
                    icon = Icons.Outlined.CameraRear,
                    isSelected = selectedLens == "main",
                    modifier = Modifier.weight(1f)
                ) {
                    if (isStreaming) AppBridgeService.requestSwitchLens("main")
                    else AppBridgeService.selectedCameraLens.value = "main"
                }

                LensOptionChip(
                    title = "Ultra (0.6x)",
                    subtitle = "Back Wide",
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

            Text(
                text = "Stream Capabilities",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )

            StreamFeatureRow(
                icon = Icons.Outlined.Videocam,
                title = "Video Output",
                subtitle = "1080p @ 30 FPS • Hardware H.264 (/dev/video10)"
            )
            StreamFeatureRow(
                icon = Icons.Outlined.Mic,
                title = "Virtual Microphone",
                subtitle = "PipeWire node • Echo cancellation"
            )
            StreamFeatureRow(
                icon = Icons.Outlined.Bolt,
                title = "Ultra-Low Latency",
                subtitle = "< 30ms • Directly piped to Chromium & Google Meet"
            )

            Spacer(modifier = Modifier.weight(1f, fill = false).height(12.dp))

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
