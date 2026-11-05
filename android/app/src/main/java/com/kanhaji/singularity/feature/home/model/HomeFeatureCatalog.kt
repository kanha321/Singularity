package com.kanhaji.singularity.feature.home.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.PowerSettingsNew

object HomeFeatureCatalog {
    fun featureCards(
        onOpenAuth: () -> Unit,
        onOpenPower: () -> Unit,
        onOpenFiles: () -> Unit,
        onOpenShare: () -> Unit = {}
    ) = listOf(
        FeatureCardModel(
            id = "auth_feature",
            title = "Authentication",
            subtitle = "Biometric policies & PAM",
            icon = Icons.Default.Fingerprint,
            statusText = "Configure",
            isHighlighted = true,
            onClick = onOpenAuth
        ),
        FeatureCardModel(
            id = "power_feature",
            title = "Power Controls",
            subtitle = "Sleep, reboot & shutdown",
            icon = Icons.Outlined.PowerSettingsNew,
            statusText = "Control",
            isHighlighted = true,
            onClick = onOpenPower
        ),
        FeatureCardModel(
            id = "share_feature",
            title = "Send to PC",
            subtitle = "Push link to desktop",
            icon = Icons.Outlined.OpenInBrowser,
            statusText = "Send",
            isHighlighted = false,
            onClick = onOpenShare
        ),
        FeatureCardModel(
            id = "files_feature",
            title = "Remote Files",
            subtitle = "Browse & stream laptop files",
            icon = Icons.Outlined.Folder,
            statusText = "Browse",
            isHighlighted = true,
            onClick = onOpenFiles
        )
    )
}
