package com.kanhaji.singularity.feature.audio.stream

import android.util.Log
import com.kanhaji.singularity.feature.audio.playback.PhoneAudioTrackPlayer
import com.kanhaji.singularity.service.bridge.AppBridgeState
import kotlinx.coroutines.*
import java.io.InputStream
import java.net.Socket

class AudioStreamClient(
    private val player: PhoneAudioTrackPlayer = PhoneAudioTrackPlayer()
) {
    private var streamJob: Job? = null
    private var socket: Socket? = null

    fun initialize(scope: CoroutineScope) {
        scope.launch {
            AppBridgeState.isListeningToLaptop.collect { listening ->
                if (listening) {
                    startStreaming(scope)
                } else {
                    stopStreaming()
                }
            }
        }
    }

    @Synchronized
    fun startStreaming(scope: CoroutineScope, host: String = "127.0.0.1", port: Int = 8921) {
        if (streamJob?.isActive == true) return
        Log.i(TAG, "Starting audio stream client connecting to $host:$port")
        player.start()
        streamJob = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(3840) // 20ms of 48kHz 16-bit stereo PCM
            while (isActive && AppBridgeState.isListeningToLaptop.value) {
                try {
                    val s = Socket(host, port)
                    s.tcpNoDelay = true
                    socket = s
                    Log.i(TAG, "Connected to laptop audio stream at $host:$port")
                    val input: InputStream = s.getInputStream()
                    while (isActive && AppBridgeState.isListeningToLaptop.value) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        if (read > 0) {
                            player.write(buffer, 0, read)
                        }
                    }
                } catch (e: Exception) {
                    if (isActive && AppBridgeState.isListeningToLaptop.value) {
                        Log.w(TAG, "Stream dropped (${e.message}), reconnecting in 1s...")
                        delay(1000)
                    }
                } finally {
                    try { socket?.close() } catch (_: Exception) {}
                    socket = null
                }
            }
            player.stop()
            Log.i(TAG, "Audio stream loop terminated")
        }
    }

    @Synchronized
    fun stopStreaming() {
        Log.i(TAG, "Stopping audio stream client")
        streamJob?.cancel()
        streamJob = null
        try { socket?.close() } catch (_: Exception) {}
        socket = null
        player.stop()
    }

    fun release() {
        stopStreaming()
        player.release()
    }

    companion object {
        private const val TAG = "AudioStreamClient"
        val instance by lazy { AudioStreamClient() }
    }
}

