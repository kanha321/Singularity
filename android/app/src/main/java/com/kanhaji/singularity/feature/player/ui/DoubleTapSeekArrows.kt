package com.kanhaji.singularity.feature.player.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun DoubleTapSeekArrows(
    isForward: Boolean,
    modifier: Modifier = Modifier
) {
    val alpha1 = remember { Animatable(0.2f) }
    val alpha2 = remember { Animatable(0.2f) }
    val alpha3 = remember { Animatable(0.2f) }

    LaunchedEffect(isForward) {
        while (true) {
            alpha1.animateTo(1f, tween(90))
            alpha2.animateTo(1f, tween(90))
            alpha3.animateTo(1f, tween(90))
            alpha1.animateTo(0.2f, tween(90))
            alpha2.animateTo(0.2f, tween(90))
            alpha3.animateTo(0.2f, tween(90))
            delay(100)
        }
    }

    val rotation = if (isForward) 0f else 180f
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy((-10).dp),
        modifier = modifier.rotate(rotation)
    ) {
        Icon(
            imageVector = Icons.Rounded.PlayArrow,
            contentDescription = null,
            tint = Color.White.copy(alpha = alpha1.value),
            modifier = Modifier.size(28.dp)
        )
        Icon(
            imageVector = Icons.Rounded.PlayArrow,
            contentDescription = null,
            tint = Color.White.copy(alpha = alpha2.value),
            modifier = Modifier.size(28.dp)
        )
        Icon(
            imageVector = Icons.Rounded.PlayArrow,
            contentDescription = null,
            tint = Color.White.copy(alpha = alpha3.value),
            modifier = Modifier.size(28.dp)
        )
    }
}
