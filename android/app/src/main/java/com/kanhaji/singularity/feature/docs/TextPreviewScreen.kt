package com.kanhaji.singularity.feature.docs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kanhaji.singularity.feature.docs.data.TextContentRepository
import com.kanhaji.singularity.feature.docs.ui.TextPreviewContent
import com.kanhaji.singularity.feature.docs.ui.TextPreviewTopBar

@Composable
fun TextPreviewScreen(
    remotePath: String,
    title: String,
    formattedSize: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var content by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var reloadTrigger by remember { mutableStateOf(0) }

    LaunchedEffect(remotePath, reloadTrigger) {
        isLoading = true
        errorMessage = null
        val result = TextContentRepository.fetchText(remotePath)
        if (result.isSuccess) {
            content = result.getOrNull()
        } else {
            errorMessage = result.exceptionOrNull()?.localizedMessage ?: "Failed to load file"
        }
        isLoading = false
    }

    val subtitle = remember(content, formattedSize) {
        val linesCount = content?.lines()?.size ?: 0
        if (linesCount > 0) "$linesCount lines • $formattedSize" else formattedSize
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TextPreviewTopBar(
                title = title,
                subtitle = subtitle,
                content = content.orEmpty(),
                onBack = onBack
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            when {
                isLoading -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Loading file...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                errorMessage != null -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = errorMessage ?: "Failed to load",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = { reloadTrigger++ }) {
                            Text("Retry")
                        }
                    }
                }
                content != null -> {
                    TextPreviewContent(
                        content = content.orEmpty(),
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
