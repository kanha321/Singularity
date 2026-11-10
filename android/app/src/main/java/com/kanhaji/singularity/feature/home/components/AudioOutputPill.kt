package com.kanhaji.singularity.feature.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Laptop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kanhaji.core.designsystem.cards.SurfaceCard
import com.kanhaji.singularity.feature.audio.model.LaptopAudioSink
import com.kanhaji.singularity.feature.audio.route.AudioRouteMapper
import com.kanhaji.singularity.feature.audio.ui.AppNameResolver
import com.kanhaji.singularity.service.bridge.AppBridgeState

@Composable
fun AudioOutputPill(
    selectedRoute: String?,
    sinks: List<LaptopAudioSink>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isListening by AppBridgeState.isListeningToLaptop.collectAsState()
    val activeRoute by AppBridgeState.activeAudioRoute.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val activeSink = sinks.find { it.name == selectedRoute }
    val isRouting = selectedRoute != null && selectedRoute != "phone" && activeSink != null
    val hasActiveOutput = activeRoute != null && activeRoute!!.isPlaying

    if (!isRouting && !isListening && !hasActiveOutput) return

    val label = when {
        hasActiveOutput -> "Playing on ${activeRoute!!.outputDeviceLabel}"
        isListening -> "Listening to Laptop Sound"
        isRouting -> "Streaming to ${AudioRouteMapper.formatDisplayName(activeSink!!)}"
        else -> "Audio Virtualization"
    }

    val sublabel = when {
        hasActiveOutput && activeRoute?.sourceApp != null -> "via ${AppNameResolver.resolve(context, activeRoute!!.sourceApp)}"
        hasActiveOutput -> "via ${activeRoute!!.sourceOrigin.replaceFirstChar { it.uppercase() }}"
        isListening -> "48kHz PCM • Low Latency"
        isRouting -> "Phone Audio Capture"
        else -> "Active Pipeline"
    }

    val isHeadphones = isListening || activeRoute?.outputDeviceName == "singularity_phone_sink"
    val icon = if (isHeadphones) Icons.Outlined.Headphones else Icons.Outlined.Laptop

    SurfaceCard(
        onClick = onClick,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
        borderWidth = 1.dp,
        borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
        cornerRadius = 16.dp,
        elevation = 0.dp,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier.size(34.dp).clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF4CAF50)))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "ACTIVE AUDIO • $sublabel",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4CAF50),
                            letterSpacing = 0.5.sp
                        )
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Icon(Icons.Outlined.GraphicEq, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        }
    }
}
