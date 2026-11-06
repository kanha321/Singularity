package com.kanhaji.singularity.feature.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import coil.imageLoader
import coil.request.ImageRequest
import com.kanhaji.singularity.R
import com.kanhaji.singularity.feature.media.model.MediaState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object MediaNotificationHelper {
    const val MEDIA_CHANNEL_ID = "singularity_media_controls_v1"
    const val MEDIA_NOTIFICATION_ID = 2831

    fun createNotificationChannel(notificationManager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                MEDIA_CHANNEL_ID,
                "Desktop Media Controls",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows native Quick Settings media player card for desktop playback"
                setShowBadge(false)
                setSound(null, null)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun buildPlaybackState(state: MediaState): PlaybackStateCompat {
        val pbState = if (state.isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        var actions = PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_SEEK_TO
        if (state.canPlay) actions = actions or PlaybackStateCompat.ACTION_PLAY
        if (state.canPause) actions = actions or PlaybackStateCompat.ACTION_PAUSE
        if (state.canNext) actions = actions or PlaybackStateCompat.ACTION_SKIP_TO_NEXT
        if (state.canPrev) actions = actions or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS

        return PlaybackStateCompat.Builder()
            .setState(pbState, state.positionMs, if (state.isPlaying) 1.0f else 0.0f)
            .setActions(actions)
            .build()
    }

    fun buildMetadata(state: MediaState, artBitmap: Bitmap?): MediaMetadataCompat {
        val builder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, state.title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, state.artist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, state.album)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, state.durationMs)

        if (artBitmap != null) {
            builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, artBitmap)
            builder.putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, artBitmap)
        }
        return builder.build()
    }

    fun buildNotification(
        context: Context,
        state: MediaState,
        artBitmap: Bitmap?,
        token: MediaSessionCompat.Token
    ): Notification {
        val piPrev = PendingIntent.getBroadcast(
            context, 101,
            Intent(context, MediaNotificationReceiver::class.java).apply {
                action = MediaNotificationReceiver.ACTION_PREVIOUS
                putExtra(MediaNotificationReceiver.EXTRA_PLAYER, state.player)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val piPlayPause = PendingIntent.getBroadcast(
            context, 102,
            Intent(context, MediaNotificationReceiver::class.java).apply {
                action = if (state.isPlaying) MediaNotificationReceiver.ACTION_PAUSE else MediaNotificationReceiver.ACTION_PLAY
                putExtra(MediaNotificationReceiver.EXTRA_PLAYER, state.player)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val piNext = PendingIntent.getBroadcast(
            context, 103,
            Intent(context, MediaNotificationReceiver::class.java).apply {
                action = MediaNotificationReceiver.ACTION_NEXT
                putExtra(MediaNotificationReceiver.EXTRA_PLAYER, state.player)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIcon = if (state.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playPauseText = if (state.isPlaying) "Pause" else "Play"

        val style = androidx.media.app.NotificationCompat.MediaStyle()
            .setMediaSession(token)
            .setShowActionsInCompactView(0, 1, 2)

        val notifBuilder = NotificationCompat.Builder(context, MEDIA_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(state.title)
            .setContentText(state.artist)
            .setSubText(state.player.split('.').lastOrNull() ?: "Desktop")
            .setStyle(style)
            .setOngoing(state.isPlaying)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(android.R.drawable.ic_media_previous, "Previous", piPrev)
            .addAction(playPauseIcon, playPauseText, piPlayPause)
            .addAction(android.R.drawable.ic_media_next, "Next", piNext)

        if (artBitmap != null) notifBuilder.setLargeIcon(artBitmap)
        return notifBuilder.build()
    }

    suspend fun loadBitmap(context: Context, url: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val req = ImageRequest.Builder(context).data(url).allowHardware(false).build()
            (context.imageLoader.execute(req).drawable as? BitmapDrawable)?.bitmap
        } catch (e: Exception) {
            Log.w("MediaNotificationHelper", "Failed to load album art bitmap: ${e.message}")
            null
        }
    }
}
