package com.kanhaji.singularity.feature.audio.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kanhaji.core.navigation.navigator.LocalNavigator
import com.kanhaji.core.shell.state.rememberCollapsingTopBarState
import com.kanhaji.core.shell.ui.ScreenScaffold
import com.kanhaji.core.shell.ui.SmoothCollapsingTopBar
import com.kanhaji.core.shell.ui.rememberCollapsingTopBarConnection
import com.kanhaji.singularity.feature.audio.route.AudioRoutePreferences
import com.kanhaji.singularity.service.bridge.AppBridgeState

@Composable
fun AudioControlScreen() {
    val sinks by AppBridgeState.availableLaptopAudioSinks.collectAsState()
    val selectedRoute by AppBridgeState.selectedAudioRoute.collectAsState()
    val isListeningToLaptop by AppBridgeState.isListeningToLaptop.collectAsState()
    val activeRoute by AppBridgeState.activeAudioRoute.collectAsState()
    val showAllDevices by AudioRoutePreferences.showAllDevices.collectAsState()
    val hiddenSinks by AudioRoutePreferences.hiddenSinkNames.collectAsState()

    val visibleSinks = remember(sinks, hiddenSinks, showAllDevices, selectedRoute, activeRoute) {
        if (showAllDevices) sinks
        else sinks.filter { sink ->
            !hiddenSinks.contains(sink.name) || selectedRoute == sink.name || activeRoute?.outputDeviceName == sink.name
        }
    }

    val navigator = LocalNavigator.current
    val scrollState = rememberScrollState()
    val collapsingState = rememberCollapsingTopBarState()
    val collapsingConnection = rememberCollapsingTopBarConnection(
        state = collapsingState,
        canExpand = { scrollState.value == 0 }
    )

    ScreenScaffold(
        modifier = Modifier.nestedScroll(collapsingConnection),
        containerColor = Color.Transparent,
        topBar = {
            SmoothCollapsingTopBar(
                title = "Audio Output",
                showBack = true,
                onBack = { navigator.pop() },
                actions = {
                    IconButton(onClick = { AudioRoutePreferences.toggleShowAllDevices() }) {
                        Icon(
                            if (showAllDevices) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = if (showAllDevices) "Show Unhidden Only" else "Manage All Devices",
                            tint = if (showAllDevices) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
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
            ActiveAudioBanner(activeRoute)

            Text("STREAM MODE & LATENCY", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
            AudioLatencyCard()

            Text("CONVERGENCE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
            AudioConvergenceCard(enabled = isListeningToLaptop, onToggle = { AppBridgeState.setLaptopToPhoneAudio(it) })

            Text("PHYSICAL OUTPUT DESTINATION", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
            val isPhoneSelected = (selectedRoute == null || selectedRoute == "phone") && !isListeningToLaptop
            val isPhoneActive = activeRoute?.outputDeviceName == "phone" || (activeRoute?.outputDeviceName == "singularity_phone_sink" && isListeningToLaptop)
            PhoneAudioRow(isSelected = isPhoneSelected, isActiveOutput = isPhoneActive, onSelect = { AppBridgeState.setAudioRoute("phone") })

            visibleSinks.forEach { sink ->
                val isSinkActive = activeRoute?.outputDeviceName == sink.name && activeRoute?.isPlaying == true
                val isSinkHidden = hiddenSinks.contains(sink.name)
                AudioSinkItemRow(
                    sink = sink,
                    isSelected = selectedRoute == sink.name && !isListeningToLaptop,
                    isActiveOutput = isSinkActive,
                    isManagementMode = showAllDevices,
                    isHidden = isSinkHidden,
                    onSelect = { AppBridgeState.setAudioRoute(sink.name) },
                    onVolumeChange = { AppBridgeState.setSinkVolume(sink.name, it) },
                    onMuteToggle = { AppBridgeState.setSinkMute(sink.name, it) },
                    onToggleHidden = { AudioRoutePreferences.toggleSinkHidden(sink.name) }
                )
            }
        }
    }
}
