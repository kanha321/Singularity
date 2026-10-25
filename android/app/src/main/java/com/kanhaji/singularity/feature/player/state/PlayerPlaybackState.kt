package com.kanhaji.singularity.feature.player.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay

class PlayerPlaybackState(
    val exoPlayer: ExoPlayer
) {
    var isPlaying by mutableStateOf(exoPlayer.isPlaying)
    var currentPosition by mutableLongStateOf(0L)
    var totalDuration by mutableLongStateOf(exoPlayer.duration.coerceAtLeast(0L))
    var isBuffering by mutableStateOf(false)
}

@Composable
fun rememberPlayerPlaybackState(
    exoPlayer: ExoPlayer,
    isSeeking: Boolean
): PlayerPlaybackState {
    val state = remember(exoPlayer) { PlayerPlaybackState(exoPlayer) }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                state.isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                state.isBuffering = playbackState == Player.STATE_BUFFERING
                state.totalDuration = exoPlayer.duration.coerceAtLeast(0L)
            }

            override fun onEvents(player: Player, events: Player.Events) {
                state.totalDuration = player.duration.coerceAtLeast(0L)
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    LaunchedEffect(state.isPlaying, isSeeking) {
        if (state.isPlaying && !isSeeking) {
            while (true) {
                state.currentPosition = exoPlayer.currentPosition
                delay(100)
            }
        }
    }

    return state
}
