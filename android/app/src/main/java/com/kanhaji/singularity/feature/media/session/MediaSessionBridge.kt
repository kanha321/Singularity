package com.kanhaji.singularity.feature.media.session

import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.util.Log
import com.kanhaji.singularity.feature.media.model.MediaState

interface MediaSessionBridge {
    val packageName: String
    val isPlaying: Boolean
    fun getMediaState(): MediaState?
    fun play()
    fun pause()
    fun skipToNext()
    fun skipToPrevious()
    fun seekTo(positionMs: Long)
}

class AndroidMediaSessionBridge(
    private val controller: MediaController
) : MediaSessionBridge {
    companion object {
        private const val TAG = "AndroidMediaSessionBridge"
    }

    override val packageName: String get() = controller.packageName

    override val isPlaying: Boolean
        get() = controller.playbackState?.state == PlaybackState.STATE_PLAYING

    override fun getMediaState(): MediaState? {
        val meta = controller.metadata
        val pbState = controller.playbackState ?: return null
        val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
        if (title.isEmpty() && pbState.state == PlaybackState.STATE_STOPPED) return null

        val artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
        val album = meta?.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: ""
        val durationMs = (meta?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L).coerceAtLeast(0L)
        val posMs = pbState.position.coerceAtLeast(0L)
        val uri = meta?.getString(MediaMetadata.METADATA_KEY_ART_URI)
            ?: meta?.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
            ?: meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
            ?: meta?.description?.iconUri?.toString()

        val bitmap = meta?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: meta?.description?.iconBitmap
            ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)

        val (artUrl, artBase64) = MediaArtHelper.extractArt(uri, bitmap, title, artist)

        val actions = pbState.actions
        val status = when (pbState.state) {
            PlaybackState.STATE_PLAYING -> "Playing"
            PlaybackState.STATE_PAUSED -> "Paused"
            else -> "Stopped"
        }

        return MediaState(
            player = packageName,
            title = title,
            artist = artist,
            album = album,
            artUrl = artUrl,
            status = status,
            positionMs = posMs,
            durationMs = durationMs,
            canPlay = (actions and PlaybackState.ACTION_PLAY) != 0L,
            canPause = (actions and PlaybackState.ACTION_PAUSE) != 0L,
            canNext = (actions and PlaybackState.ACTION_SKIP_TO_NEXT) != 0L,
            canPrev = (actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS) != 0L,
            canSeek = (actions and PlaybackState.ACTION_SEEK_TO) != 0L,
            artBase64 = artBase64
        )
    }

    override fun play() {
        try { controller.transportControls.play() } catch (e: Exception) {
            Log.w(TAG, "Failed play: ${e.message}")
        }
    }

    override fun pause() {
        try { controller.transportControls.pause() } catch (e: Exception) {
            Log.w(TAG, "Failed pause: ${e.message}")
        }
    }

    override fun skipToNext() {
        try { controller.transportControls.skipToNext() } catch (e: Exception) {
            Log.w(TAG, "Failed next: ${e.message}")
        }
    }

    override fun skipToPrevious() {
        try { controller.transportControls.skipToPrevious() } catch (e: Exception) {
            Log.w(TAG, "Failed previous: ${e.message}")
        }
    }

    override fun seekTo(positionMs: Long) {
        try { controller.transportControls.seekTo(positionMs) } catch (e: Exception) {
            Log.w(TAG, "Failed seekTo: ${e.message}")
        }
    }
}

