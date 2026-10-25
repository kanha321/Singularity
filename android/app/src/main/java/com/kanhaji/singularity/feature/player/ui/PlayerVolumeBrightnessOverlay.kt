package com.kanhaji.singularity.feature.player.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeMute
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.BrightnessHigh
import androidx.compose.material.icons.rounded.BrightnessLow
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kanhaji.singularity.feature.player.state.VolumeBrightnessType
import com.kanhaji.singularity.feature.player.state.VolumeBrightnessUiState

@Composable
fun PlayerVolumeBrightnessOverlay(
    uiState: VolumeBrightnessUiState?,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = uiState != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier.fillMaxSize()
    ) {
        if (uiState == null) return@AnimatedVisibility

        val animatedFraction by animateFloatAsState(
            targetValue = uiState.percent.coerceIn(0, 100) / 100f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            label = "vol_bright"
        )

        val icon = when (uiState.type) {
            VolumeBrightnessType.VOLUME -> when {
                animatedFraction <= 0f -> Icons.AutoMirrored.Rounded.VolumeMute
                animatedFraction < 0.33f -> Icons.AutoMirrored.Rounded.VolumeMute
                animatedFraction < 0.66f -> Icons.AutoMirrored.Rounded.VolumeDown
                else -> Icons.AutoMirrored.Rounded.VolumeUp
            }
            VolumeBrightnessType.BRIGHTNESS -> when {
                animatedFraction < 0.33f -> Icons.Rounded.BrightnessLow
                animatedFraction < 0.66f -> Icons.Rounded.BrightnessMedium
                else -> Icons.Rounded.BrightnessHigh
            }
        }

        val alignOnRight = uiState.alignOnRight

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = if (alignOnRight) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .padding(
                        start = if (!alignOnRight) 32.dp else 0.dp,
                        end = if (alignOnRight) 32.dp else 0.dp
                    )
                    .width(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.Black.copy(alpha = 0.82f))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
                    .padding(vertical = 16.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .width(6.dp)
                        .height(120.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White.copy(alpha = 0.20f))
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .width(6.dp)
                            .fillMaxHeight(fraction = animatedFraction)
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "${(animatedFraction * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }
    }
}
