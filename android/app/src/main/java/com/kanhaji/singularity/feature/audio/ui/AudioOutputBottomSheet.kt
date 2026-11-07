package com.kanhaji.singularity.feature.audio.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kanhaji.core.designsystem.cards.SurfaceCard
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
            if (activeRoute != null && activeRoute!!.isPlaying) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF4CAF50).copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, Color(0xFF4CAF50).copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF4CAF50)))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "ACTIVE OUTPUT: ${activeRoute!!.outputDeviceLabel}${activeRoute!!.sourceApp?.let { " (via $it)" } ?: ""}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4CAF50)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            LazyColumn(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Text("CONVERGENCE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    AudioConvergenceCard(enabled = isListeningToLaptop, onToggle = { AppBridgeState.setLaptopToPhoneAudio(it) })
                }
                item {
                    Spacer(modifier = Modifier.height(4.dp))
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

@Composable
private fun PhoneAudioRow(isSelected: Boolean, isActiveOutput: Boolean = false, onSelect: () -> Unit) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    val cardBg = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)

    SurfaceCard(
        onClick = onSelect,
        color = cardBg,
        borderWidth = if (isSelected) 1.5.dp else 1.dp,
        borderColor = borderColor,
        cornerRadius = 16.dp,
        elevation = 0.dp,
        modifier = Modifier.fillMaxWidth().height(68.dp)
    ) {
        Row(modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .border(1.dp, if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Smartphone, null, Modifier.size(22.dp), tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("This Phone", style = MaterialTheme.typography.titleMedium, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium, fontSize = 15.sp)
                    if (isActiveOutput) {
                        Spacer(modifier = Modifier.width(6.dp))
                        AudioActiveBadge()
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text("Phone speakers or wired/BT headset", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            Box(modifier = Modifier.size(22.dp).clip(CircleShape).background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent).border(1.5.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape), contentAlignment = Alignment.Center) {
                if (isSelected) Icon(Icons.Outlined.Check, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}
