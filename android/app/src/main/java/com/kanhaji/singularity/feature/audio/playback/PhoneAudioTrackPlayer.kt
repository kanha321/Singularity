package com.kanhaji.singularity.feature.audio.playback

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log

class PhoneAudioTrackPlayer(
    val sampleRate: Int = 48000,
    val channelConfig: Int = AudioFormat.CHANNEL_OUT_STEREO,
    val audioFormat: Int = AudioFormat.ENCODING_PCM_16BIT
) {
    private var audioTrack: AudioTrack? = null
    val bufferSize: Int = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat).coerceAtLeast(15360)
    private var isPrebuffered = false
    private var bufferedBytes = 0
    private val prebufferThreshold = 7680

    @Synchronized
    fun start() {
        if (audioTrack == null) {
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setFlags(AudioAttributes.FLAG_LOW_LATENCY)
                .build()

            val format = AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setChannelMask(channelConfig)
                .setEncoding(audioFormat)
                .build()

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build()
        }
        isPrebuffered = false
        bufferedBytes = 0
    }

    @Synchronized
    fun write(data: ByteArray, offset: Int, size: Int): Int {
        val track = audioTrack ?: return -1
        val written = track.write(data, offset, size, AudioTrack.WRITE_BLOCKING)
        if (written > 0 && !isPrebuffered) {
            bufferedBytes += written
            if (bufferedBytes >= prebufferThreshold) {
                try {
                    track.play()
                    isPrebuffered = true
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start AudioTrack: ${e.message}")
                }
            }
        }
        return written
    }

    @Synchronized
    fun stop() {
        try {
            audioTrack?.stop()
            audioTrack?.flush()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop AudioTrack: ${e.message}")
        } finally {
            isPrebuffered = false
            bufferedBytes = 0
        }
    }

    @Synchronized
    fun release() {
        try {
            audioTrack?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release AudioTrack: ${e.message}")
        } finally {
            audioTrack = null
            isPrebuffered = false
            bufferedBytes = 0
        }
    }

    fun isPlaying(): Boolean = audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING

    companion object {
        private const val TAG = "PhoneAudioTrackPlayer"
    }
}
