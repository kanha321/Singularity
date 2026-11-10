package com.kanhaji.singularity.feature.audio.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeMute
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kanhaji.singularity.feature.audio.model.LaptopAudioSink
import com.kanhaji.singularity.feature.audio.route.AudioRouteMapper
import kotlin.math.roundToInt

@Composable
fun AudioSinkItemRow(
    sink: LaptopAudioSink,
    isSelected: Boolean,
    isActiveOutput: Boolean = false,
    isManagementMode: Boolean = false,
    isHidden: Boolean = false,
    onSelect: () -> Unit,
    onVolumeChange: (Int) -> Unit,
    onMuteToggle: (Boolean) -> Unit,
    onToggleHidden: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var totalCardWidthPx by remember { mutableFloatStateOf(1f) }
    var actionZoneWidthPx by remember { mutableFloatStateOf(0f) }
    var localVol by remember(sink.name) { mutableFloatStateOf(sink.volume.toFloat()) }
    var isUserDragging by remember { mutableStateOf(false) }
    var lastUserTouchTime by remember { mutableLongStateOf(0L) }
    var lastSentVolume by remember { mutableIntStateOf(sink.volume) }
    var lastSentTime by remember { mutableLongStateOf(0L) }

    LaunchedEffect(sink.volume) {
        val now = System.currentTimeMillis()
        if (!isUserDragging && (now - lastUserTouchTime > 1000L)) {
            localVol = sink.volume.toFloat()
            lastSentVolume = sink.volume
        }
    }

    val targetFraction = if (isSelected) (localVol / 100f).coerceIn(0f, 1f) else 0f
    val animatedFraction by animateFloatAsState(targetFraction, tween(200, easing = FastOutSlowInEasing), label = "slider_progress")
    val displayFraction = if (isUserDragging) targetFraction else animatedFraction

    val targetBorder = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    val animatedBorder by animateColorAsState(targetBorder, tween(200), label = "border_anim")

    val targetFill = when {
        !isSelected -> Color.Transparent
        sink.mute -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
    }
    val animatedFill by animateColorAsState(targetFill, tween(200), label = "fill_anim")

    Box(
        modifier = modifier.fillMaxWidth().height(68.dp)
            .then(if (isHidden) Modifier.alpha(0.6f) else Modifier)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .border(if (isSelected) 1.5.dp else 1.dp, animatedBorder, RoundedCornerShape(16.dp))
            .onSizeChanged { totalCardWidthPx = it.width.toFloat().coerceAtLeast(1f) }
            .audioSliderGesture(
                key = sink.name, isSelected = isSelected, totalWidthPx = totalCardWidthPx,
                actionZoneWidthPx = actionZoneWidthPx, onSelect = onSelect,
                onDragStateChanged = { isUserDragging = it },
                onVolumeAdjust = { newVol ->
                    val now = System.currentTimeMillis(); lastUserTouchTime = now; localVol = newVol.toFloat()
                    if (newVol != lastSentVolume && (now - lastSentTime > 50L)) {
                        lastSentVolume = newVol; lastSentTime = now; onVolumeChange(newVol)
                    }
                },
                onVolumeFinished = { finalVol ->
                    if (finalVol != lastSentVolume) { lastSentVolume = finalVol; onVolumeChange(finalVol) }
                }
            )
    ) {
        if (displayFraction > 0f) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(displayFraction).background(animatedFill))
        }
        Row(Modifier.fillMaxSize().padding(start = 14.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(modifier = Modifier.fillMaxHeight().weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .border(1.dp, if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(AudioDeviceIconHelper.getDeviceIcon(sink.deviceType), null, Modifier.size(22.dp), tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(AudioRouteMapper.formatDisplayName(sink), style = MaterialTheme.typography.titleMedium, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (isHidden) {
                            Spacer(Modifier.width(6.dp)); AudioHiddenBadge()
                        } else if (isActiveOutput) {
                            Spacer(Modifier.width(6.dp)); AudioActiveBadge()
                        } else if (sink.isDefault) {
                            Spacer(Modifier.width(6.dp)); AudioDefaultBadge()
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(AudioSliderHelper.formatVolumeSubtitle(localVol.roundToInt(), sink.mute), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }
            Row(
                modifier = Modifier.onSizeChanged { actionZoneWidthPx = it.width.toFloat() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isManagementMode) {
                    IconButton(onClick = { onToggleHidden?.invoke() }, modifier = Modifier.size(44.dp)) {
                        Icon(
                            if (isHidden) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = if (isHidden) "Unhide" else "Hide",
                            modifier = Modifier.size(22.dp),
                            tint = if (isHidden) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    IconButton(onClick = { onMuteToggle(!sink.mute) }, modifier = Modifier.size(44.dp)) {
                        Icon(if (sink.mute || localVol <= 0f) Icons.AutoMirrored.Outlined.VolumeMute else Icons.AutoMirrored.Outlined.VolumeUp, "Mute", Modifier.size(22.dp), tint = if (sink.mute) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    }
                    Box(
                        modifier = Modifier.size(32.dp).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onSelect),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(Modifier.size(22.dp).clip(CircleShape).background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent).border(1.5.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape), contentAlignment = Alignment.Center) {
                            if (isSelected) Icon(Icons.Outlined.Check, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
            }
        }
    }
}
