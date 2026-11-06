package com.kanhaji.singularity.feature.media

import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Handler
import android.os.Looper
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import coil.imageLoader
import coil.request.ImageRequest
import com.kanhaji.singularity.feature.media.model.MediaAction
import com.kanhaji.singularity.feature.media.model.MediaState
import com.kanhaji.singularity.service.AppBridgeService
import kotlinx.coroutines.*

class MediaControlManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "MediaControlManager"
        const val MEDIA_CHANNEL_ID = MediaNotificationHelper.MEDIA_CHANNEL_ID
        const val MEDIA_NOTIFICATION_ID = MediaNotificationHelper.MEDIA_NOTIFICATION_ID

        @Volatile
        private var INSTANCE: MediaControlManager? = null

        fun getInstance(context: Context): MediaControlManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MediaControlManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private var mediaSession: MediaSessionCompat? = null
    private var currentMediaState: MediaState? = null
    private var cachedArtUrl: String? = null
    private var cachedArtBitmap: Bitmap? = null

    init {
        MediaNotificationHelper.createNotificationChannel(notificationManager)
    }

    private fun getOrCreateMediaSession(): MediaSessionCompat {
        val existing = mediaSession
        if (existing != null) return existing
        val session = MediaSessionCompat(context, "SingularityMedia").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() { dispatchCommand("play", currentMediaState?.player ?: "") }
                override fun onPause() { dispatchCommand("pause", currentMediaState?.player ?: "") }
                override fun onSkipToNext() { dispatchCommand("next", currentMediaState?.player ?: "") }
                override fun onSkipToPrevious() { dispatchCommand("previous", currentMediaState?.player ?: "") }
                override fun onSeekTo(pos: Long) { dispatchCommand("seek", currentMediaState?.player ?: "", pos) }
            }, Handler(Looper.getMainLooper()))
        }
        this.mediaSession = session
        return session
    }

    fun dispatchCommand(command: String, player: String, positionMs: Long? = null) {
        Log.i(TAG, "Dispatching media command: $command on $player (pos: $positionMs)")
        AppBridgeService.pendingMediaAction.set(MediaAction(player, command, positionMs))
    }

    fun updateMedia(state: MediaState?) {
        scope.launch(Dispatchers.Main) { handleUpdateMedia(state) }
    }

    private fun handleUpdateMedia(state: MediaState?) {
        if (state == null || (state.title.isEmpty() && !state.isPlaying)) {
            handleClearMedia()
            return
        }

        currentMediaState = state
        val session = getOrCreateMediaSession()
        session.isActive = true
        session.setPlaybackState(MediaNotificationHelper.buildPlaybackState(state))

        if (state.artUrl != cachedArtUrl) {
            cachedArtUrl = state.artUrl
            cachedArtBitmap = null
            if (!state.artUrl.isNullOrEmpty()) {
                scope.launch {
                    val bitmap = loadBitmap(state.artUrl)
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
        notificationManager.notify(MEDIA_NOTIFICATION_ID, notif)
    }

    private suspend fun loadBitmap(url: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val req = ImageRequest.Builder(context).data(url).allowHardware(false).build()
            (context.imageLoader.execute(req).drawable as? BitmapDrawable)?.bitmap
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load album art bitmap: ${e.message}")
            null
        }
    }

    fun clearMedia() {
        scope.launch(Dispatchers.Main) { handleClearMedia() }
    }

    private fun handleClearMedia() {
        currentMediaState = null
        cachedArtUrl = null
        cachedArtBitmap = null
        mediaSession?.isActive = false
        notificationManager.cancel(MEDIA_NOTIFICATION_ID)
    }

    fun release() {
        scope.launch(Dispatchers.Main) {
            handleClearMedia()
            mediaSession?.release()
            mediaSession = null
            scope.cancel()
        }
    }
}
