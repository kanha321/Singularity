package com.kanhaji.singularity.feature.player.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kanhaji.singularity.feature.player.state.DoubleTapSeekEvent

@Composable
fun PlayerDoubleTapOverlay(
    seekEvent: DoubleTapSeekEvent?,
    modifier: Modifier = Modifier
) {
    val isForward = seekEvent?.isForward ?: true

    AnimatedVisibility(
        visible = seekEvent != null,
        enter = fadeIn(animationSpec = tween(120, easing = LinearOutSlowInEasing)),
        exit = fadeOut(animationSpec = tween(300, easing = FastOutLinearInEasing)),
        modifier = modifier.fillMaxSize()
    ) {
        if (seekEvent == null) return@AnimatedVisibility

        val alignment = if (isForward) Alignment.CenterEnd else Alignment.CenterStart
        val shape = if (isForward) RightSideOvalShape() else LeftSideOvalShape()

        val tintAlpha = remember { Animatable(0.28f) }
        LaunchedEffect(seekEvent.accumulatedSeconds) {
            tintAlpha.snapTo(0.28f)
            tintAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 420, easing = FastOutLinearInEasing)
            )
        }

        val currentAlpha = tintAlpha.value
        val gradientColors = if (isForward) {
            listOf(Color.Transparent, Color.White.copy(alpha = currentAlpha))
        } else {
            listOf(Color.White.copy(alpha = currentAlpha), Color.Transparent)
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = alignment
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.38f)
                    .clip(shape)
                    .background(Brush.horizontalGradient(gradientColors)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DoubleTapSeekArrows(isForward = isForward)
                    AnimatedContent(
                        targetState = seekEvent.accumulatedSeconds,
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(140)) + scaleIn(initialScale = 0.88f))
                                .togetherWith(fadeOut(animationSpec = tween(120)))
                        },
                        label = "seconds_fade"
                    ) { seconds ->
                        Text(
                            text = "$seconds seconds",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
    }
}
