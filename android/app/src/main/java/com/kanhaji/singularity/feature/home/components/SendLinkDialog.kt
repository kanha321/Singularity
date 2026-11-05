package com.kanhaji.singularity.feature.home.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.unit.dp
import com.kanhaji.singularity.feature.share.ShareReceiverActivity
import com.kanhaji.singularity.ui.components.SingularityDialog

@Composable
fun SendLinkDialog(
    onDismiss: () -> Unit,
    onSuccess: (String) -> Unit
) {
    var urlText by remember { mutableStateOf("") }
    val clipboardManager = LocalClipboardManager.current

    SingularityDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Outlined.OpenInBrowser,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                "Send to Desktop",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Enter or paste any link to instantly open it on your PC.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    placeholder = { Text("https://example.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = {
                            val clip = clipboardManager.getText()?.text
                            if (!clip.isNullOrBlank()) {
                                urlText = clip.trim()
                            }
                        }) {
                            Icon(Icons.Outlined.ContentPaste, contentDescription = "Paste from clipboard")
                        }
                    }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val target = ShareReceiverActivity.extractUrl(urlText) ?: urlText.trim()
                    if (target.isNotEmpty()) {
                        ShareReceiverActivity.dispatchUrlToDesktop(target)
                        onSuccess(target)
                        onDismiss()
                    }
                },
                enabled = urlText.isNotBlank()
            ) {
                Text("Open on PC")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

