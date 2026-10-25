package com.kanhaji.singularity.feature.player.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun PlayerControlsOverlay(
    visible: Boolean,
    title: String,
    isPlaying: Boolean,
    isBuffering: Boolean,
    currentPosition: Long,
    totalDuration: Long,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekStart: () -> Unit,
    onSeekEnd: (Long) -> Unit,
    onProgressChange: (Long) -> Unit,
    onUserActivity: () -> Unit,
    onDismissControls: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(180)),
        exit = fadeOut(animationSpec = tween(220)),
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.40f))
        ) {
            PlayerTopBar(
                title = title,
                onBack = onBack,
                modifier = Modifier.align(Alignment.TopCenter)
            )

            PlayerCenterControls(
                isPlaying = isPlaying,
                isBuffering = isBuffering,
                onPlayPause = {
                    onUserActivity()
                    onPlayPause()
                },
                modifier = Modifier.align(Alignment.Center)
            )

            PlayerBottomBar(
                currentPosition = currentPosition,
                totalDuration = totalDuration,
                isPlaying = isPlaying,
                onSeekStart = {
                    onUserActivity()
                    onSeekStart()
                },
                onSeekEnd = { position ->
                    onUserActivity()
                    onSeekEnd(position)
                },
                onProgressChange = { position ->
                    onUserActivity()
                    onProgressChange(position)
                },
                onUserActivity = onUserActivity,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}
