package com.kanhaji.singularity.feature.home.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Videocam

object HomeFeatureCatalog {
    fun featureCards(
        onOpenAuth: () -> Unit,
        onOpenPower: () -> Unit,
        onOpenFiles: () -> Unit,
        onOpenCamera: () -> Unit,
        isCameraActive: Boolean = false
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
            id = "camera_feature",
            title = "Continuity Camera",
            subtitle = "Webcam & mic streaming",
            icon = Icons.Outlined.Videocam,
            statusText = if (isCameraActive) "Live" else "Webcam",
            isHighlighted = isCameraActive,
            onClick = onOpenCamera
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
