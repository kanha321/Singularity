package com.kanhaji.singularity.feature.files.components

import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import com.kanhaji.singularity.ui.components.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun FileBrowserTopBarActions(
    isSudoElevated: Boolean,
    onToggleSudo: () -> Unit,
    onOpenPlaces: () -> Unit,
    onOpenOptions: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        IconButton(onClick = onToggleSudo) {
            Icon(
                imageVector = if (isSudoElevated) Icons.Filled.Shield else Icons.Outlined.AdminPanelSettings,
                contentDescription = if (isSudoElevated) "Sudo Active" else "Sudo Mode",
                tint = if (isSudoElevated) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onOpenPlaces) {
            Icon(Icons.Outlined.Bookmarks, contentDescription = "Quick Places")
        }
        IconButton(onClick = onOpenOptions) {
            Icon(Icons.Outlined.Tune, contentDescription = "View Options")
        }
    }
}
