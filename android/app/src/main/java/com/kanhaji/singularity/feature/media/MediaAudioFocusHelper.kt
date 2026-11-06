package com.kanhaji.singularity.feature.media

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log

class MediaAudioFocusHelper(
    private val audioManager: AudioManager?,
    private val onPauseRequested: () -> Unit,
    private val onPlayRequested: () -> Unit = {},
    private val isMusicActiveProvider: (() -> Boolean)? = null,
    private val postDelayed: ((Runnable, Long) -> Unit)? = null,
    private val removeCallbacks: ((Runnable) -> Unit)? = null
) {
    companion object {
        private const val TAG = "MediaAudioFocusHelper"
        internal const val CHECK_INTERVAL_MS = 750L
        internal const val REQUIRED_INACTIVE_CHECKS = 2
    }

    constructor(
        context: Context,
        onPauseRequested: () -> Unit,
        onPlayRequested: () -> Unit
    ) : this(
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager,
        onPauseRequested,
        onPlayRequested
    )

    private val handler by lazy { Handler(Looper.getMainLooper()) }
    private val schedule = postDelayed ?: { r, delayMs -> handler.postDelayed(r, delayMs); Unit }
    private val cancelSchedule = removeCallbacks ?: { r -> handler.removeCallbacks(r) }

    var hasFocus: Boolean = false
        internal set
    var wasPausedByFocusLoss: Boolean = false
        internal set

    internal var inactiveCheckCount = 0

    internal val resumeCheckRunnable = object : Runnable {
        override fun run() {
            if (!wasPausedByFocusLoss) return
            val active = isMusicActiveProvider?.invoke() ?: (audioManager?.isMusicActive == true)
            if (!active) {
                inactiveCheckCount++
                if (inactiveCheckCount >= REQUIRED_INACTIVE_CHECKS) {
                    Log.i(TAG, "Android media finished -> requesting focus & resuming laptop")
                    val granted = audioManager == null || requestFocus()
                    if (granted) {
                        wasPausedByFocusLoss = false
                        inactiveCheckCount = 0
                        onPlayRequested()
                        return
                    }
                }
            } else {
                inactiveCheckCount = 0
            }
            schedule(this, CHECK_INTERVAL_MS)
        }
    }

    internal val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        Log.d(TAG, "Audio focus changed: $focusChange")
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                hasFocus = false
                wasPausedByFocusLoss = true
                inactiveCheckCount = 0
                cancelSchedule(resumeCheckRunnable)
                schedule(resumeCheckRunnable, 1000L)
                onPauseRequested()
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasFocus = true
                cancelSchedule(resumeCheckRunnable)
                if (wasPausedByFocusLoss) {
                    wasPausedByFocusLoss = false
                    inactiveCheckCount = 0
                    onPlayRequested()
                }
            }
        }
    }

    private var focusRequest: AudioFocusRequest? = null

    private fun getOrCreateFocusRequest(): AudioFocusRequest? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (focusRequest == null) {
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
                focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(attrs)
                    .setAcceptsDelayedFocusGain(false)
                    .setWillPauseWhenDucked(true)
                    .setOnAudioFocusChangeListener(focusChangeListener, handler)
                    .build()
            }
        }
        return focusRequest
    }

    fun requestFocus(): Boolean {
        if (hasFocus) return true
        val am = audioManager ?: return false
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getOrCreateFocusRequest()?.let { am.requestAudioFocus(it) } ?: AudioManager.AUDIOFOCUS_REQUEST_FAILED
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(focusChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }
        hasFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        Log.d(TAG, "Requested audio focus, granted=$hasFocus")
        return hasFocus
    }

    fun abandonFocus(clearFocusLoss: Boolean = true) {
        if (clearFocusLoss) {
            wasPausedByFocusLoss = false
            inactiveCheckCount = 0
            cancelSchedule(resumeCheckRunnable)
        }
        if (!hasFocus) return
        hasFocus = false
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { am.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            am.abandonAudioFocus(focusChangeListener)
        }
        Log.d(TAG, "Abandoned audio focus")
    }
}
