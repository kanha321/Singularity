package com.kanhaji.singularity.feature.audio.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kanhaji.singularity.service.bridge.AppBridgeState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioOutputBottomSheet(onDismiss: () -> Unit) {
    val sinks by AppBridgeState.availableLaptopAudioSinks.collectAsState()
    val selectedRoute by AppBridgeState.selectedAudioRoute.collectAsState()
    val isListeningToLaptop by AppBridgeState.isListeningToLaptop.collectAsState()
    val activeRoute by AppBridgeState.activeAudioRoute.collectAsState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Audio Output", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Text("Unified cross-device audio", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, contentDescription = "Close") }
            }
            ActiveAudioBanner(activeRoute)
            Spacer(modifier = Modifier.height(14.dp))
            LazyColumn(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Text("STREAM MODE & LATENCY", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    AudioLatencyCard()
                }
                item {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("CONVERGENCE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    AudioConvergenceCard(enabled = isListeningToLaptop, onToggle = { AppBridgeState.setLaptopToPhoneAudio(it) })
                }
                item {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("PHYSICAL OUTPUT DESTINATION", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    val isPhoneSelected = (selectedRoute == null || selectedRoute == "phone") && !isListeningToLaptop
                    val isPhoneActive = activeRoute?.outputDeviceName == "phone" || (activeRoute?.outputDeviceName == "singularity_phone_sink" && isListeningToLaptop)
                    PhoneAudioRow(isSelected = isPhoneSelected, isActiveOutput = isPhoneActive, onSelect = { AppBridgeState.setAudioRoute("phone") })
                }
                items(sinks, key = { it.name }) { sink ->
                    val isSinkActive = activeRoute?.outputDeviceName == sink.name && activeRoute?.isPlaying == true
                    AudioSinkItemRow(
                        sink = sink,
                        isSelected = selectedRoute == sink.name && !isListeningToLaptop,
                        isActiveOutput = isSinkActive,
                        onSelect = { AppBridgeState.setAudioRoute(sink.name) },
                        onVolumeChange = { AppBridgeState.setSinkVolume(sink.name, it) },
                        onMuteToggle = { AppBridgeState.setSinkMute(sink.name, it) }
                    )
                }
                item { Spacer(modifier = Modifier.height(28.dp)) }
            }
        }
    }
}
