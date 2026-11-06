package com.kanhaji.singularity.feature.media.session

import android.util.Log
import com.kanhaji.singularity.feature.media.model.MediaAction
import com.kanhaji.singularity.feature.media.model.MediaState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PhoneMediaSessionTracker(
    private val selfPackageName: String = "com.kanhaji.singularity"
) {
    companion object {
        private const val TAG = "PhoneMediaSessionTracker"
    }

    private val _currentMedia = MutableStateFlow<MediaState?>(null)
    val currentMedia: StateFlow<MediaState?> = _currentMedia.asStateFlow()

    private var activeSessions: List<MediaSessionBridge> = emptyList()

    fun updateSessions(sessions: List<MediaSessionBridge>) {
        val filtered = sessions.filter { it.packageName != selfPackageName }
        activeSessions = filtered
        refreshCurrentMedia()
    }

    fun refreshCurrentMedia() {
        if (activeSessions.isEmpty()) {
            _currentMedia.value = null
            return
        }
        val playing = activeSessions.firstOrNull { it.isPlaying }
        val candidate = playing ?: activeSessions.firstOrNull()
        val newState = candidate?.getMediaState()
        _currentMedia.value = newState
        Log.d(TAG, "Refreshed phone media: ${newState?.player} - ${newState?.title} (${newState?.status})")
    }

    fun executeAction(action: MediaAction): Boolean {
        if (activeSessions.isEmpty()) return false
        val target = if (action.player.isNotEmpty()) {
            activeSessions.firstOrNull { it.packageName.equals(action.player, ignoreCase = true) }
                ?: activeSessions.firstOrNull { it.isPlaying }
                ?: activeSessions.firstOrNull()
        } else {
            activeSessions.firstOrNull { it.isPlaying } ?: activeSessions.firstOrNull()
        } ?: return false

        Log.i(TAG, "Dispatching phone media action: ${action.command} to ${target.packageName}")
        return when (action.command.lowercase()) {
            "play" -> { target.play(); true }
            "pause" -> { target.pause(); true }
            "play_pause" -> {
                if (target.isPlaying) target.pause() else target.play()
                true
            }
            "next" -> { target.skipToNext(); true }
            "previous" -> { target.skipToPrevious(); true }
            "seek" -> {
                target.seekTo(action.positionMs ?: 0L)
                true
            }
            else -> false
        }
    }

    fun clear() {
        activeSessions = emptyList()
        _currentMedia.value = null
    }
}

