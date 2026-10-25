package com.kanhaji.singularity.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kanhaji.core.designsystem.cards.SurfaceCard
import com.kanhaji.core.designsystem.feedback.rememberHapticClick
import kotlinx.coroutines.delay

object AppSnackbar {
    var currentMessage by mutableStateOf<String?>(null)
        private set
    var hasFab by mutableStateOf(false)
    private var messageId = 0L

    fun show(message: String) {
        messageId++
        currentMessage = message
    }

    fun dismiss() {
        currentMessage = null
    }

    val currentId: Long get() = messageId
}

@Composable
fun SingularitySnackbarHost(
    modifier: Modifier = Modifier
) {
    val message = AppSnackbar.currentMessage
    val messageId = AppSnackbar.currentId
    val hasFab = AppSnackbar.hasFab

    LaunchedEffect(messageId) {
        if (message != null) {
            delay(3000L)
            AppSnackbar.dismiss()
        }
    }

    val density = LocalDensity.current
    val navBarsBottom = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
    val bottomOffset = if (hasFab) navBarsBottom + 84.dp else navBarsBottom + 16.dp

    AnimatedVisibility(
        visible = message != null,
        enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(),
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = bottomOffset)
    ) {
        SurfaceCard(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
            borderWidth = 1.dp,
            borderColor = MaterialTheme.colorScheme.primary,
            cornerRadius = 18.dp,
            elevation = 0.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    text = message.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = rememberHapticClick { AppSnackbar.dismiss() },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Dismiss",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
