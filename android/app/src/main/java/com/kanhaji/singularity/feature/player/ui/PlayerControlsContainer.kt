package com.kanhaji.singularity.feature.player.ui

import android.app.Activity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.media3.exoplayer.ExoPlayer
import com.kanhaji.singularity.feature.player.state.PlayerDoubleTapState
import com.kanhaji.singularity.feature.player.state.PlayerSpeedState
import com.kanhaji.singularity.feature.player.state.PlayerSwipeSeekState
import com.kanhaji.singularity.feature.player.state.PlayerVolumeBrightnessState
import com.kanhaji.singularity.feature.player.state.rememberPlayerPlaybackState
import com.kanhaji.singularity.feature.player.util.PlayerHardwareController
import kotlinx.coroutines.delay

@Composable
fun PlayerControlsContainer(
    exoPlayer: ExoPlayer,
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = context as? Activity

    var showControls by remember { mutableStateOf(true) }
    var isSeeking by remember { mutableStateOf(false) }
    var activityEventId by remember { mutableIntStateOf(0) }

    val playback = rememberPlayerPlaybackState(exoPlayer = exoPlayer, isSeeking = isSeeking)

    val doubleTapState = remember(exoPlayer) {
        PlayerDoubleTapState(
            scope = coroutineScope,
            seekStepSeconds = 10,
            resetDelayMillis = 650L,
            onSeek = { deltaSeconds ->
                val maxDur = exoPlayer.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
                val target = (exoPlayer.currentPosition + deltaSeconds * 1000L).coerceIn(0L, maxDur)
                exoPlayer.seekTo(target)
                playback.currentPosition = target
            }
        )
    }
    val seekEvent by doubleTapState.seekEvent.collectAsState()

    val volumeBrightnessState = remember(context, activity) {
        PlayerVolumeBrightnessState(
            scope = coroutineScope,
            onVolumeChange = { PlayerHardwareController.setVolume(context, it) },
            onBrightnessChange = { pct -> activity?.let { PlayerHardwareController.setScreenBrightness(it, pct) } }
        )
    }
    val volumeBrightnessUiState by volumeBrightnessState.uiState.collectAsState()

    val swipeSeekState = remember(exoPlayer) {
        PlayerSwipeSeekState(scope = coroutineScope, onSeekCommit = { pos -> exoPlayer.seekTo(pos); playback.currentPosition = pos })
    }
    val swipeSeekUiState by swipeSeekState.uiState.collectAsState()

    val speedState = remember(exoPlayer) {
        PlayerSpeedState(baselineSpeed = 2.0f, onPlaybackSpeedChange = { exoPlayer.setPlaybackSpeed(it) })
    }
    val speedUiState by speedState.uiState.collectAsState()

    fun notifyUserActivity() { activityEventId++ }

    LaunchedEffect(showControls, activityEventId, isSeeking, swipeSeekUiState, speedUiState.isHolding) {
        if (showControls && !isSeeking && swipeSeekUiState == null && !speedUiState.isHolding) {
            delay(3000)
            showControls = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .playerTapGestures(
                doubleTapState = doubleTapState,
                onSingleTap = { showControls = !showControls; if (showControls) notifyUserActivity() },
                onDoubleTapSeek = { isForward -> showControls = false; doubleTapState.handleDoubleTap(isForward = isForward) },
                onPlayPause = { notifyUserActivity(); if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play() }
            )
            .playerSwipeGestures(
                volumeBrightnessState = volumeBrightnessState,
                swipeSeekState = swipeSeekState,
                speedState = speedState,
                getInitialBrightness = { activity?.let { PlayerHardwareController.getScreenBrightness(it) } ?: 50 },
                getInitialVolume = { PlayerHardwareController.getVolume(context) },
                getCurrentPosition = { exoPlayer.currentPosition },
                getTotalDuration = { exoPlayer.duration.coerceAtLeast(0L) },
                getCurrentSpeed = { exoPlayer.playbackParameters.speed },
                onDismissControls = { showControls = false }
            )
    ) {
        PlayerDoubleTapOverlay(seekEvent = seekEvent)
        PlayerVolumeBrightnessOverlay(uiState = volumeBrightnessUiState)
        PlayerSwipeSeekOverlay(uiState = swipeSeekUiState)
        PlayerSpeedOverlay(uiState = speedUiState, onUnlockClick = { speedState.onUnlock() })

        PlayerControlsOverlay(
            visible = showControls,
            title = title,
            isPlaying = playback.isPlaying,
            isBuffering = playback.isBuffering,
            currentPosition = playback.currentPosition,
            totalDuration = playback.totalDuration,
            onBack = onBack,
            onPlayPause = { notifyUserActivity(); if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play() },
            onSeekStart = { notifyUserActivity(); isSeeking = true },
            onSeekEnd = { pos -> notifyUserActivity(); exoPlayer.seekTo(pos); isSeeking = false },
            onProgressChange = { pos -> notifyUserActivity(); playback.currentPosition = pos },
            onUserActivity = { notifyUserActivity() },
            onDismissControls = { showControls = false }
        )
    }
}
