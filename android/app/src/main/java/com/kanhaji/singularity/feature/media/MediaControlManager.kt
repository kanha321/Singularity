package com.kanhaji.singularity.feature.media

import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import com.kanhaji.singularity.feature.media.model.MediaAction
import com.kanhaji.singularity.feature.media.model.MediaState
import com.kanhaji.singularity.service.AppBridgeService
import kotlinx.coroutines.*

class MediaControlManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "MediaControlManager"
        @Volatile private var INSTANCE: MediaControlManager? = null

        fun getInstance(context: Context): MediaControlManager =
            INSTANCE ?: synchronized(this) { INSTANCE ?: MediaControlManager(context.applicationContext).also { INSTANCE = it } }
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private var mediaSession: MediaSessionCompat? = null
    private var currentMediaState: MediaState? = null
    private var cachedArtUrl: String? = null
    private var cachedArtBitmap: Bitmap? = null
    private var pauseRequestedAtMs = 0L
    private var lastPausedPlayer = ""

    val remoteVolumeManager by lazy {
        RemoteVolumeManager({ MediaSessionVolumeDelegate(getOrCreateMediaSession()) }, scope)
    }

    private val audioFocusHelper = MediaAudioFocusHelper(
        context = context,
        onPauseRequested = {
            val player = currentMediaState?.player ?: ""
            if (player.isNotEmpty() && currentMediaState?.isPlaying == true) {
                lastPausedPlayer = player; pauseRequestedAtMs = System.currentTimeMillis(); dispatchCommand("pause", player)
            }
        },
        onPlayRequested = {
            val player = lastPausedPlayer.ifEmpty { currentMediaState?.player ?: "" }
            if (player.isNotEmpty() && currentMediaState?.isPlaying == false) {
                pauseRequestedAtMs = 0L; dispatchCommand("play", player)
            }
        }
    )

    init {
        MediaNotificationHelper.createNotificationChannel(notificationManager)
        remoteVolumeManager.initialize()
    }

    private fun getOrCreateMediaSession(): MediaSessionCompat = mediaSession ?: MediaSessionCompat(context, "SingularityMedia").apply {
        setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() = play(currentMediaState?.player ?: "")
            override fun onPause() = pause(currentMediaState?.player ?: "")
            override fun onSkipToNext() = dispatchCommand("next", currentMediaState?.player ?: "")
            override fun onSkipToPrevious() = dispatchCommand("previous", currentMediaState?.player ?: "")
            override fun onSeekTo(pos: Long) = dispatchCommand("seek", currentMediaState?.player ?: "", pos)
        }, Handler(Looper.getMainLooper()))
    }.also { mediaSession = it }

    fun play(player: String = currentMediaState?.player ?: "") { pauseRequestedAtMs = 0L; audioFocusHelper.requestFocus(); dispatchCommand("play", player) }
    fun pause(player: String = currentMediaState?.player ?: "") { pauseRequestedAtMs = System.currentTimeMillis(); audioFocusHelper.abandonFocus(clearFocusLoss = true); dispatchCommand("pause", player) }

    fun requestFocus() = play()

    fun dispatchCommand(command: String, player: String, positionMs: Long? = null) {
        Log.i(TAG, "Dispatching: $command on $player (pos: $positionMs)")
        AppBridgeService.pendingMediaAction.set(MediaAction(player, command, positionMs))
    }

    fun updateMedia(state: MediaState?) = scope.launch(Dispatchers.Main) { handleUpdateMedia(state) }

    private fun handleUpdateMedia(state: MediaState?) {
        if (state == null || (state.title.isEmpty() && !state.isPlaying)) {
            handleClearMedia()
            return
        }
        currentMediaState = state
        val recentlyPaused = System.currentTimeMillis() - pauseRequestedAtMs < 1000L
        if (!state.isPlaying) {
            if (!audioFocusHelper.wasPausedByFocusLoss) {
                pauseRequestedAtMs = 0L; audioFocusHelper.abandonFocus(clearFocusLoss = true)
            }
        } else if (!recentlyPaused) {
            lastPausedPlayer = state.player; audioFocusHelper.requestFocus()
        }
        val session = getOrCreateMediaSession().apply {
            isActive = true
            setPlaybackState(MediaNotificationHelper.buildPlaybackState(state))
        }
        if (state.artUrl != cachedArtUrl) {
            cachedArtUrl = state.artUrl
            cachedArtBitmap = null
            if (!state.artUrl.isNullOrEmpty()) {
                scope.launch {
                    val bitmap = MediaNotificationHelper.loadBitmap(context, state.artUrl)
                    if (cachedArtUrl == state.artUrl) {
                        cachedArtBitmap = bitmap
                        updateSessionAndNotification(state, bitmap)
                    }
                }
            }
        }
        updateSessionAndNotification(state, cachedArtBitmap)
    }

    private fun updateSessionAndNotification(state: MediaState, artBitmap: Bitmap?) {
        val session = getOrCreateMediaSession()
        session.setMetadata(MediaNotificationHelper.buildMetadata(state, artBitmap))
        val notif = MediaNotificationHelper.buildNotification(context, state, artBitmap, session.sessionToken)
        notificationManager.notify(MediaNotificationHelper.MEDIA_NOTIFICATION_ID, notif)
    }

    fun clearMedia() = scope.launch(Dispatchers.Main) { handleClearMedia() }

    private fun handleClearMedia() {
        lastPausedPlayer = ""
        audioFocusHelper.abandonFocus(clearFocusLoss = true)
        currentMediaState = null; cachedArtUrl = null; cachedArtBitmap = null
        if (remoteVolumeManager.currentProvider == null) {
            mediaSession?.isActive = false
        } else {
            remoteVolumeManager.restoreStandbySession()
        }
        notificationManager.cancel(MediaNotificationHelper.MEDIA_NOTIFICATION_ID)
    }

    fun release() = scope.launch(Dispatchers.Main) {
        handleClearMedia()
        mediaSession?.release()
        mediaSession = null
        scope.cancel()
    }
}
