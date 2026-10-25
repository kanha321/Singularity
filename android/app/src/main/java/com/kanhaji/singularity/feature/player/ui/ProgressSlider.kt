package com.kanhaji.singularity.feature.player.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ProgressSlider(
    currentPosition: Long,
    totalDuration: Long,
    isPlaying: Boolean,
    onSeekStart: () -> Unit,
    onSeekEnd: (Long) -> Unit,
    onProgressChange: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }
    var sliderValue by remember { mutableFloatStateOf(0f) }

    val baseProgress = if (totalDuration > 0) currentPosition.toFloat() / totalDuration else 0f
    val targetProgress = if (isDragging) sliderValue else baseProgress

    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress.coerceIn(0f, 1f),
        animationSpec = if (isDragging) {
            snap()
        } else {
            tween(durationMillis = 50, easing = FastOutSlowInEasing)
        },
        label = "seekProgress"
    )

    val visualProgress = if (isDragging) sliderValue else animatedProgress

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp)
    ) {
        LinearWavyProgressIndicator(
            progress = { visualProgress },
            waveSpeed = if (isPlaying && !isDragging) WavyProgressIndicatorDefaults.LinearDeterminateWavelength else 0.dp,
            color = MaterialTheme.colorScheme.primary,
            trackColor = Color.White.copy(alpha = 0.25f),
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .align(Alignment.Center)
        )

        Slider(
            value = visualProgress,
            onValueChange = { newProgress: Float ->
                if (!isDragging) {
                    isDragging = true
                    onSeekStart()
                }
                sliderValue = newProgress
                val newPosition = (newProgress * totalDuration).roundToLong()
                onProgressChange(newPosition)
            },
            onValueChangeFinished = {
                val finalPosition = (sliderValue * totalDuration).roundToLong()
                onSeekEnd(finalPosition)
                isDragging = false
            },
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .align(Alignment.Center)
        )
    }
}
