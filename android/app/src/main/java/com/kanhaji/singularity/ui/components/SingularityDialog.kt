package com.kanhaji.singularity.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kanhaji.core.designsystem.backgrounds.AnimatedBackground
import com.kanhaji.core.theme.manager.ThemeManager

@Composable
fun SingularityDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    confirmButton: (@Composable () -> Unit)? = null,
    dismissButton: (@Composable () -> Unit)? = null,
    properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false)
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = properties
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.background,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .widthIn(max = 420.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                AnimatedBackground(
                    type = ThemeManager.backgroundType,
                    enableParallax = false,
                    modifier = Modifier.matchParentSize()
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    if (icon != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(bottom = 16.dp)
                        ) {
                            icon()
                        }
                    }

                    if (title != null) {
                        CompositionLocalProvider(
                            LocalContentColor provides MaterialTheme.colorScheme.onBackground,
                            LocalTextStyle provides MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                        ) {
                            Box(modifier = Modifier.padding(bottom = if (text != null) 12.dp else 20.dp)) {
                                title()
                            }
                        }
                    }

                    if (text != null) {
                        CompositionLocalProvider(
                            LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant,
                            LocalTextStyle provides MaterialTheme.typography.bodyMedium
                        ) {
                            Box(modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
                                text()
                            }
                        }
                    }

                    if (confirmButton != null || dismissButton != null) {
                        Row(
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (dismissButton != null) {
                                dismissButton()
                                if (confirmButton != null) Spacer(modifier = Modifier.width(8.dp))
                            }
                            if (confirmButton != null) {
                                confirmButton()
                            }
                        }
                    }
                }
            }
        }
    }
}
