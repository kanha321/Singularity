package com.kanhaji.singularity.feature.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.kanhaji.singularity.feature.player.ui.PlayerSurface
import com.kanhaji.singularity.feature.player.util.VideoFileUtils
import com.kanhaji.singularity.service.AppBridgeService

@Composable
fun VideoPlayerScreen(
    remotePath: String,
    title: String,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {}
) {
    val fileServerUrl by AppBridgeService.fileServerUrl.collectAsState()

    val baseUrl = fileServerUrl ?: "http://127.0.0.1:8920"
    val streamingUrl = VideoFileUtils.buildStreamingUrl(baseUrl, remotePath)

    BackHandler {
        onBack()
    }

    Box(modifier = modifier.fillMaxSize()) {
        PlayerSurface(
            videoUrl = streamingUrl,
            modifier = Modifier.fillMaxSize(),
            controls = { exoPlayer ->
                com.kanhaji.singularity.feature.player.ui.PlayerControlsContainer(
                    exoPlayer = exoPlayer,
                    title = title,
                    onBack = onBack
                )
            }
        )
    }
}
