package com.kanhaji.singularity.feature.home.components

import androidx.compose.animation.Crossfade
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun HomeLockFab(
    isPcConnected: Boolean,
    isPcLocked: Boolean,
    modifier: Modifier = Modifier
) {
    Crossfade(targetState = isPcLocked && isPcConnected, label = "fabModeCrossfade") { locked ->
        if (locked) {
            UnlockFab(modifier = modifier)
        } else {
            HoldToLockFab(
                isPcConnected = isPcConnected,
                modifier = modifier
            )
        }
    }
}
