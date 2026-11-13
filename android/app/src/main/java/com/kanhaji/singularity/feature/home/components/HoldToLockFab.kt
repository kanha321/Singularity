package com.kanhaji.singularity.feature.home.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun HoldToLockFab(
    isPcConnected: Boolean,
    isLocking: Boolean = false,
    modifier: Modifier = Modifier
) {
    HomeLockFab(
        isPcConnected = isPcConnected,
        isLoggedIn = true,
        isPcLocked = false,
        isLocking = isLocking,
        modifier = modifier
    )
}
