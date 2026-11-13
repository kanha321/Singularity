package com.kanhaji.singularity.feature.home.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun HomeAuthFab(
    modifier: Modifier = Modifier
) {
    HomeLockFab(
        isPcConnected = true,
        isLoggedIn = true,
        isPcLocked = true,
        isLocking = false,
        modifier = modifier
    )
}
