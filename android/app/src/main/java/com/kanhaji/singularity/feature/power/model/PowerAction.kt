package com.kanhaji.singularity.feature.power.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.ui.graphics.vector.ImageVector

enum class PowerAction(
    val actionId: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector
) {
    REBOOT(
        actionId = "reboot",
        title = "Reboot",
        subtitle = "Restart system cleanly",
        icon = Icons.Outlined.RestartAlt
    ),
    SHUTDOWN(
        actionId = "shutdown",
        title = "Shutdown",
        subtitle = "Power off laptop",
        icon = Icons.Outlined.PowerSettingsNew
    ),
    SUSPEND(
        actionId = "suspend",
        title = "Suspend",
        subtitle = "Lock screen & sleep",
        icon = Icons.Outlined.Bedtime
    ),
    HIBERNATE(
        actionId = "hibernate",
        title = "Hibernate",
        subtitle = "Lock & save to swap",
        icon = Icons.Outlined.AcUnit
    ),
    LOGOUT(
        actionId = "logout",
        title = "Logout",
        subtitle = "End user desktop session",
        icon = Icons.AutoMirrored.Outlined.Logout
    );

    companion object {
        val allActions = listOf(REBOOT, SHUTDOWN, SUSPEND, HIBERNATE, LOGOUT)
        val loggedOutActions = listOf(REBOOT, SHUTDOWN)

        fun actionsFor(isLoggedIn: Boolean): List<PowerAction> =
            if (isLoggedIn) allActions else loggedOutActions
    }
}
