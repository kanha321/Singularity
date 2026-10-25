package com.kanhaji.singularity.feature.files.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kanhaji.singularity.feature.files.model.FsViewOptions
import com.kanhaji.singularity.feature.files.model.SortDirection
import com.kanhaji.singularity.feature.files.model.SortField

import com.kanhaji.singularity.ui.components.IconButton
import com.kanhaji.singularity.ui.feedback.rememberHapticClick

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FsViewOptionsSheet(
    options: FsViewOptions,
    onOptionsChange: (FsViewOptions) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "View Options",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Text(
                text = "Sort By",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(SortField.entries.toTypedArray()) { field ->
                    FilterChip(
                        selected = options.sortBy == field,
                        onClick = rememberHapticClick { onOptionsChange(options.copy(sortBy = field)) },
                        label = { Text(field.displayName) }
                    )
                }
            }

            Text(
                text = "Direction",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = options.direction == SortDirection.ASCENDING,
                    onClick = rememberHapticClick { onOptionsChange(options.copy(direction = SortDirection.ASCENDING)) },
                    label = { Text("Ascending") }
                )
                FilterChip(
                    selected = options.direction == SortDirection.DESCENDING,
                    onClick = rememberHapticClick { onOptionsChange(options.copy(direction = SortDirection.DESCENDING)) },
                    label = { Text("Descending") }
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Show Hidden Files", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                    Text(text = "Files starting with a dot (.)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val onToggleHidden = rememberHapticClick { onOptionsChange(options.copy(showHidden = !options.showHidden)) }
                Switch(
                    checked = options.showHidden,
                    onCheckedChange = { onToggleHidden() }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Keep Folders on Top", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                    Text(text = "Show directories before files", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val onToggleFolders = rememberHapticClick { onOptionsChange(options.copy(foldersFirst = !options.foldersFirst)) }
                Switch(
                    checked = options.foldersFirst,
                    onCheckedChange = { onToggleFolders() }
                )
            }
        }
    }
}
