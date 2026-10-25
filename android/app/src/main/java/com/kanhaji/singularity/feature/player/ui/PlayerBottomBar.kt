package com.kanhaji.singularity.feature.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import com.kanhaji.singularity.feature.player.util.PlayerTimeUtils

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember

@Composable
fun PlayerBottomBar(
    currentPosition: Long,
    totalDuration: Long,
    isPlaying: Boolean,
    onSeekStart: () -> Unit,
    onSeekEnd: (Long) -> Unit,
    onProgressChange: (Long) -> Unit,
    onUserActivity: () -> Unit,
    modifier: Modifier = Modifier
) {
    val layoutDirection = LocalLayoutDirection.current
    val insets = WindowInsets.safeDrawing.union(WindowInsets.displayCutout).asPaddingValues()
    val bottomPadding = maxOf(insets.calculateBottomPadding(), 12.dp) + 6.dp
    val startPadding = insets.calculateStartPadding(layoutDirection) + 16.dp
    val endPadding = insets.calculateEndPadding(layoutDirection) + 16.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {}
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.85f)
                    )
                )
            )
            .padding(
                start = startPadding,
                end = endPadding,
                top = 8.dp,
                bottom = bottomPadding
            )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${PlayerTimeUtils.formatTime(currentPosition)} / ${PlayerTimeUtils.formatTime(totalDuration)}",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium
                )
                PlayerRotationButton(onClick = onUserActivity)
            }

            ProgressSlider(
                currentPosition = currentPosition,
                totalDuration = totalDuration,
                isPlaying = isPlaying,
                onSeekStart = onSeekStart,
                onSeekEnd = onSeekEnd,
                onProgressChange = onProgressChange
            )
        }
    }
}
