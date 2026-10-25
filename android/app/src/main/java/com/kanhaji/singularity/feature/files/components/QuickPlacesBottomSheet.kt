package com.kanhaji.singularity.feature.files.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import com.kanhaji.singularity.ui.components.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kanhaji.singularity.feature.files.data.QuickPlacesRepository
import com.kanhaji.singularity.feature.files.security.FsBiometricHelper
import com.kanhaji.singularity.feature.files.security.FsSudoSessionManager
import com.kanhaji.singularity.feature.files.util.formatFolderTitle
import com.kanhaji.singularity.ui.components.AppSnackbar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickPlacesBottomSheet(
    currentPath: String,
    onDismissRequest: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val places by QuickPlacesRepository.places.collectAsState()
    val sudoState by FsSudoSessionManager.state.collectAsState()
    val context = LocalContext.current
    val isSudoActive = sudoState.isElevated
    val isCurrentBookmarked = QuickPlacesRepository.isBookmarked(currentPath)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Quick Places",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )

                if (!isCurrentBookmarked && currentPath.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            val name = formatFolderTitle(currentPath)
                            QuickPlacesRepository.addBookmark(name, currentPath)
                            AppSnackbar.show("Bookmarked $name")
                        }
                    ) {
                        Icon(imageVector = Icons.Outlined.BookmarkAdd, contentDescription = null)
                        Text(text = " Bookmark", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(places, key = { it.id }) { place ->
                    QuickPlaceItemRow(
                        place = place,
                        isSudoActive = isSudoActive,
                        onClick = {
                            if (place.isProtected && !isSudoActive) {
                                FsBiometricHelper.promptSudoElevation(
                                    context = context,
                                    title = "Sudo Required for ${place.name}",
                                    subtitle = "Verify fingerprint to open ${place.path}",
                                    onSuccess = {
                                        onDismissRequest()
                                        onNavigate(place.path)
                                    }
                                )
                            } else {
                                onDismissRequest()
                                onNavigate(place.path)
                            }
                        },
                        onDelete = if (place.isCustomBookmark) {
                            { QuickPlacesRepository.removeBookmark(place.path) }
                        } else null
                    )
                }
            }
        }
    }
}
