package com.kanhaji.singularity.feature.player.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.LockReset
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kanhaji.singularity.feature.player.state.SpeedUiState

@Composable
fun PlayerSpeedOverlay(
    uiState: SpeedUiState,
    onUnlockClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val speed = uiState.speed
    val isHolding = uiState.isHolding
    val isLocked = uiState.isLocked
    val formattedSpeed = if (speed % 1.0f == 0f) "%.0fX".format(speed) else "%.2fX".format(speed).trimEnd('0').trimEnd('.')

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = isHolding || isLocked,
            enter = fadeIn() + scaleIn(initialScale = 0.85f),
            exit = fadeOut() + scaleOut(targetScale = 0.85f),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .padding(top = 24.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.85f))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val icon = if (isLocked) Icons.Rounded.Lock else if (speed < 1f) Icons.Rounded.FastRewind else Icons.Rounded.FastForward
                    Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isLocked) "$formattedSpeed Locked" else "$formattedSpeed Speed",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White
                    )
                }
                if (!isLocked) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(text = "Slide ↕ speed • ↔ edge to lock", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, color = Color.White.copy(alpha = 0.7f))
                }
            }
        }

        val showSideBtn = isHolding || isLocked
        val isLeft = uiState.isHoldingOnLeft
        val isHovered = if (isLeft) uiState.isHoveringLeftLock else uiState.isHoveringRightLock

        AnimatedVisibility(
            visible = showSideBtn,
            enter = fadeIn() + scaleIn(initialScale = 0.7f),
            exit = fadeOut() + scaleOut(targetScale = 0.7f),
            modifier = Modifier.align(if (isLeft) Alignment.CenterStart else Alignment.CenterEnd).padding(start = if (isLeft) 20.dp else 0.dp, end = if (!isLeft) 20.dp else 0.dp)
        ) {
            val scale by animateFloatAsState(targetValue = if (isHovered) 1.18f else 1.0f, animationSpec = tween(150), label = "lockScale")
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .scale(scale)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(enabled = isLocked, interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onUnlockClick)
                    .padding(4.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(46.dp).clip(CircleShape).background(if (isHovered) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.85f)).border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                ) {
                    val icon = if (isLocked) Icons.Rounded.LockReset else if (isHovered) Icons.Rounded.Lock else Icons.Rounded.LockOpen
                    Icon(imageVector = icon, contentDescription = null, tint = if (isHovered) Color.Black else Color.White, modifier = Modifier.size(22.dp))
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isLocked) "Tap to Unlock" else if (isHovered) "Release to Lock" else "Slide to Lock",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.sp,
                    color = if (isHovered) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.8f)
                )
            }
        }
    }
}
