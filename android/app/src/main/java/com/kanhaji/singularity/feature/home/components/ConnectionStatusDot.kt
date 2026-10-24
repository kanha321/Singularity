package com.kanhaji.singularity.feature.home.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kanhaji.core.designsystem.feedback.rememberHapticClick
import com.kanhaji.singularity.ui.components.AppSnackbar

@Composable
fun ConnectionStatusDot(
    isPcConnected: Boolean,
    modifier: Modifier = Modifier
) {
    val green = Color(0xFF4ADE80)
    val red = Color(0xFFF87171)

    val targetColor = if (isPcConnected) green else red
    val animatedColor by animateColorAsState(targetValue = targetColor, label = "statusColor")

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isPcConnected) 1.55f else 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val onClick = rememberHapticClick {
        AppSnackbar.show(
            if (isPcConnected) "PC: Connected (Arch Linux)" else "PC: Offline / Disconnected"
        )
    }

    Box(
        modifier = modifier
            .size(36.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        // Glowing halo ring
        Box(
            modifier = Modifier
                .size(16.dp)
                .scale(pulseScale)
                .background(animatedColor.copy(alpha = 0.22f), shape = CircleShape)
        )
        // Solid core dot
        Box(
            modifier = Modifier
                .size(9.dp)
                .background(animatedColor, shape = CircleShape)
                .border(1.dp, Color.White.copy(alpha = 0.3f), shape = CircleShape)
        )
    }
}
