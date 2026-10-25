package com.kanhaji.singularity.feature.home.components

import androidx.compose.animation.Crossfade
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

enum class HomeFabMode {
    OFFLINE,
    LOGIN,
    UNLOCK,
    LOCK
}

fun resolveFabMode(
    isPcConnected: Boolean,
    isLoggedIn: Boolean,
    isPcLocked: Boolean
): HomeFabMode = when {
    !isPcConnected -> HomeFabMode.OFFLINE
    !isLoggedIn -> HomeFabMode.LOGIN
    isPcLocked -> HomeFabMode.UNLOCK
    else -> HomeFabMode.LOCK
}

@Composable
fun HomeLockFab(
    isPcConnected: Boolean,
    isLoggedIn: Boolean,
    isPcLocked: Boolean,
    modifier: Modifier = Modifier
) {
    val mode = resolveFabMode(
        isPcConnected = isPcConnected,
        isLoggedIn = isLoggedIn,
        isPcLocked = isPcLocked
    )

    Crossfade(targetState = mode, label = "fabModeCrossfade") { currentMode ->
        when (currentMode) {
            HomeFabMode.LOGIN -> HomeAuthFab(isLogin = true, modifier = modifier)
            HomeFabMode.UNLOCK -> HomeAuthFab(isLogin = false, modifier = modifier)
            HomeFabMode.LOCK, HomeFabMode.OFFLINE -> HoldToLockFab(
                isPcConnected = isPcConnected,
                modifier = modifier
            )
        }
    }
}
