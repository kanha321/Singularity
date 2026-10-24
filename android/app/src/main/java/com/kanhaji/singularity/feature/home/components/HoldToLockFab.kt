package com.kanhaji.singularity.feature.home.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.kanhaji.singularity.service.AppBridgeService
import com.kanhaji.singularity.ui.components.AppSnackbar

@Composable
fun HoldToLockFab(
    isPcConnected: Boolean,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    var isPressed by remember { mutableStateOf(false) }
    var hasTriggered by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }

    val trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    val progressColor = Color(0xFFF87171)
    val containerColor = if (isPcConnected) Color(0xFF1E222D) else MaterialTheme.colorScheme.surfaceVariant
    val iconTint = if (isPcConnected) Color(0xFFE2E8F0) else Color(0xFF64748B)

    val animatedScale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "fabScale"
    )

    LaunchedEffect(isPressed) {
        if (isPressed) {
            hasTriggered = false
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 950, easing = LinearEasing)
            )
            if (progress.value >= 1f && !hasTriggered) {
                hasTriggered = true
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                AppBridgeService.requestPcLock()
                AppSnackbar.show("Locking PC...")
                progress.snapTo(0f)
            }
        } else {
            progress.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
            )
        }
    }

    val strokeWidthPx = with(density) { 3.5.dp.toPx() }

    Box(
        modifier = modifier
            .size(60.dp)
            .scale(animatedScale)
            .pointerInput(isPcConnected) {
                detectTapGestures(
                    onPress = {
                        if (!isPcConnected) {
                            AppSnackbar.show("PC: Offline / Disconnected")
                            return@detectTapGestures
                        }
                        val startTime = System.currentTimeMillis()
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                        if (System.currentTimeMillis() - startTime < 400 && !hasTriggered) {
                            AppSnackbar.show("Hold to lock PC screen")
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.size(53.dp),
            shape = CircleShape,
            color = containerColor,
            shadowElevation = 4.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Hold to lock PC",
                    tint = iconTint,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Canvas(modifier = Modifier.size(60.dp)) {
            val halfStroke = strokeWidthPx / 2f
            val arcSize = Size(size.width - strokeWidthPx, size.height - strokeWidthPx)
            val topLeft = Offset(halfStroke, halfStroke)
            drawArc(trackColor, 0f, 360f, false, topLeft, arcSize, style = Stroke(strokeWidthPx))
            if (progress.value > 0f) {
                drawArc(
                    progressColor, -90f, 360f * progress.value, false, topLeft, arcSize,
                    style = Stroke(strokeWidthPx, cap = StrokeCap.Round)
                )
            }
        }
    }
}
