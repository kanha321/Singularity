package com.kanhaji.singularity.feature.audio.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Laptop
import androidx.compose.material.icons.outlined.Speaker
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.ui.graphics.vector.ImageVector

object AudioDeviceIconHelper {
    fun getDeviceIcon(deviceType: String): ImageVector = when (deviceType.lowercase()) {
        "hdmi", "monitor", "displayport" -> Icons.Outlined.Tv
        "headphones", "headset" -> Icons.Outlined.Headphones
        "speaker" -> Icons.Outlined.Speaker
        else -> Icons.Outlined.Laptop
    }
}

