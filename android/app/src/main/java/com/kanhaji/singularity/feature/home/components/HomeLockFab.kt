package com.kanhaji.singularity.feature.home.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.kanhaji.singularity.feature.home.util.HomeBiometricLauncher
import com.kanhaji.singularity.service.AppBridgeService
import com.kanhaji.singularity.service.bridge.AppBridgeState
import com.kanhaji.singularity.ui.components.AppSnackbar
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

enum class HomeFabMode {
    OFFLINE,
    UNLOCK,
    LOCK
}

fun resolveFabMode(
    isPcConnected: Boolean,
    isLoggedIn: Boolean = true,
    isPcLocked: Boolean
): HomeFabMode = when {
    !isPcConnected -> HomeFabMode.OFFLINE
    isPcLocked -> HomeFabMode.UNLOCK
    else -> HomeFabMode.LOCK
}

@Composable
fun HomeLockFab(
    isPcConnected: Boolean,
    isLoggedIn: Boolean,
    isPcLocked: Boolean,
    isLocking: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    val mode = resolveFabMode(
        isPcConnected = isPcConnected,
        isLoggedIn = isLoggedIn,
        isPcLocked = isPcLocked
    )

    val activeLocking = isLocking && isPcConnected && mode != HomeFabMode.UNLOCK

    var isPressed by remember { mutableStateOf(false) }
    var hasTriggered by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }

    val trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    val progressColor = MaterialTheme.colorScheme.primary

    val infiniteTransition = rememberInfiniteTransition(label = "haloAndBreathe")

    val haloRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "haloRotation"
    )

    val breatheScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breatheScale"
    )

    val breatheAlpha by infiniteTransition.animateFloat(
        initialValue = 0.72f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breatheAlpha"
    )

    val containerColor by animateColorAsState(
        targetValue = when (mode) {
            HomeFabMode.UNLOCK -> MaterialTheme.colorScheme.primaryContainer
            HomeFabMode.LOCK -> if (activeLocking) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            }
            HomeFabMode.OFFLINE -> MaterialTheme.colorScheme.surfaceVariant
        },
        animationSpec = tween(durationMillis = 280),
        label = "containerColor"
    )

    val iconTint by animateColorAsState(
        targetValue = when (mode) {
            HomeFabMode.UNLOCK -> MaterialTheme.colorScheme.onPrimaryContainer
            HomeFabMode.LOCK -> if (activeLocking) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            }
            HomeFabMode.OFFLINE -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        },
        animationSpec = tween(durationMillis = 280),
        label = "iconTint"
    )

    val animatedScale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "fabScale"
    )

    val ringAlpha by animateFloatAsState(
        targetValue = if (mode == HomeFabMode.UNLOCK) 0f else 1f,
        animationSpec = tween(durationMillis = 250),
        label = "ringAlpha"
    )

    LaunchedEffect(activeLocking) {
        if (activeLocking) {
            delay(3500L)
            AppBridgeState.isLockingPc.value = false
        }
    }

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
            .pointerInput(mode, activeLocking) {
                detectTapGestures(
                    onPress = {
                        if (mode == HomeFabMode.OFFLINE) {
                            AppSnackbar.show("PC: Offline / Disconnected")
                            return@detectTapGestures
                        }
                        if (mode == HomeFabMode.UNLOCK) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            HomeBiometricLauncher.launchUnlock(context, onNotice = AppSnackbar::show)
                            return@detectTapGestures
                        }
                        if (activeLocking) {
                            AppSnackbar.show("Locking PC screen...")
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
            modifier = Modifier.size(54.dp),
            shape = CircleShape,
            color = containerColor,
            shadowElevation = if (mode == HomeFabMode.UNLOCK || activeLocking) 6.dp else 4.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Crossfade(
                    targetState = mode == HomeFabMode.UNLOCK,
                    animationSpec = tween(durationMillis = 250),
                    label = "iconCrossfade"
                ) { isUnlockMode ->
                    if (isUnlockMode) {
                        Icon(
                            imageVector = Icons.Default.Fingerprint,
                            contentDescription = "Unlock PC Screen",
                            tint = iconTint,
                            modifier = Modifier.size(28.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = if (activeLocking) "Locking PC..." else "Hold to lock PC",
                            tint = iconTint.copy(alpha = if (activeLocking) breatheAlpha else 1f),
                            modifier = Modifier
                                .size(24.dp)
                                .scale(if (activeLocking) breatheScale else 1f)
                        )
                    }
                }
            }
        }

        if (ringAlpha > 0f) {
            Canvas(modifier = Modifier.size(60.dp)) {
                val halfStroke = strokeWidthPx / 2f
                val arcSize = Size(size.width - strokeWidthPx, size.height - strokeWidthPx)
                val topLeft = Offset(halfStroke, halfStroke)
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = (size.width - strokeWidthPx) / 2f

                drawArc(
                    trackColor.copy(alpha = trackColor.alpha * ringAlpha),
                    0f, 360f, false, topLeft, arcSize, style = Stroke(strokeWidthPx)
                )

                if (activeLocking) {
                    drawArc(
                        color = progressColor.copy(alpha = 0.28f * ringAlpha),
                        startAngle = haloRotation - 90f,
                        sweepAngle = 100f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(strokeWidthPx * 1.6f, cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = progressColor.copy(alpha = progressColor.alpha * ringAlpha),
                        startAngle = haloRotation - 90f,
                        sweepAngle = 75f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(strokeWidthPx, cap = StrokeCap.Round)
                    )
                    val tipDeg = (haloRotation - 90f + 75f).toDouble()
                    val tipRad = Math.toRadians(tipDeg)
                    val tipX = center.x + (radius * cos(tipRad)).toFloat()
                    val tipY = center.y + (radius * sin(tipRad)).toFloat()

                    drawCircle(
                        color = progressColor.copy(alpha = progressColor.alpha * ringAlpha),
                        radius = strokeWidthPx * 0.75f,
                        center = Offset(tipX, tipY)
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.85f * ringAlpha),
                        radius = strokeWidthPx * 0.4f,
                        center = Offset(tipX, tipY)
                    )
                } else if (progress.value > 0f) {
                    drawArc(
                        progressColor.copy(alpha = progressColor.alpha * ringAlpha),
                        -90f, 360f * progress.value, false, topLeft, arcSize,
                        style = Stroke(strokeWidthPx, cap = StrokeCap.Round)
                    )
                }
            }
        }
    }
}
