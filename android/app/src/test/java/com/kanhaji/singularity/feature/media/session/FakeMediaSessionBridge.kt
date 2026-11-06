package com.kanhaji.singularity.feature.media.session

import com.kanhaji.singularity.feature.media.model.MediaState

class FakeMediaSessionBridge(
    override val packageName: String,
    override var isPlaying: Boolean,
    var state: MediaState?
) : MediaSessionBridge {
    var playCount = 0
    var pauseCount = 0
    var nextCount = 0
    var prevCount = 0
    var lastSeekPos: Long? = null

    override fun getMediaState(): MediaState? = state
    override fun play() { playCount++; isPlaying = true }
    override fun pause() { pauseCount++; isPlaying = false }
    override fun skipToNext() { nextCount++ }
    override fun skipToPrevious() { prevCount++ }
    override fun seekTo(positionMs: Long) { lastSeekPos = positionMs }
}

