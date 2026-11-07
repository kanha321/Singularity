package com.kanhaji.singularity.feature.media

import android.media.AudioManager
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.media.VolumeProviderCompat
import com.kanhaji.singularity.feature.audio.model.LaptopAudioSink
import com.kanhaji.singularity.feature.audio.route.AudioRouteMapper
import com.kanhaji.singularity.service.bridge.AppBridgeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

interface SessionVolumeDelegate {
    fun setPlaybackToRemote(volumeProvider: VolumeProviderCompat)
    fun setPlaybackToLocal(stream: Int)
    fun setSessionActive(active: Boolean, title: String)
}

class MediaSessionVolumeDelegate(private val session: MediaSessionCompat) : SessionVolumeDelegate {
    override fun setPlaybackToRemote(volumeProvider: VolumeProviderCompat) = session.setPlaybackToRemote(volumeProvider)
    override fun setPlaybackToLocal(stream: Int) = session.setPlaybackToLocal(stream)
    override fun setSessionActive(active: Boolean, title: String) {
        session.isActive = active
        if (active) {
            val currentMedia = AppBridgeState.currentMediaState.value
            if (currentMedia == null || currentMedia.title.isEmpty()) {
                val state = PlaybackStateCompat.Builder()
                    .setState(PlaybackStateCompat.STATE_PLAYING, 0L, 1.0f)
                    .setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE)
                    .build()
                session.setPlaybackState(state)
                val meta = MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "Laptop Audio")
                    .build()
                session.setMetadata(meta)
            }
        }
    }
}

class RemoteVolumeManager(
    private val getDelegate: () -> SessionVolumeDelegate?,
    private val scope: CoroutineScope
) {
    var activeSinkName: String? = null
        private set
    var currentProvider: VolumeProviderCompat? = null
        private set

    fun initialize() {
        scope.launch {
            combine(AppBridgeState.selectedAudioRoute, AppBridgeState.availableLaptopAudioSinks) { route, sinks ->
                Pair(route, sinks)
            }.collect { (route, sinks) ->
                updateVolumeBinding(route, sinks)
            }
        }
    }

    fun restoreStandbySession() {
        val delegate = getDelegate() ?: return
        val sink = AppBridgeState.availableLaptopAudioSinks.value.find { it.name == activeSinkName }
            ?: AppBridgeState.availableLaptopAudioSinks.value.firstOrNull()
        val displayName = if (sink != null) AudioRouteMapper.formatDisplayName(sink) else "Laptop Audio"
        delegate.setSessionActive(true, displayName)
    }

    fun updateVolumeBinding(route: String?, sinks: List<LaptopAudioSink>) {
        val delegate = getDelegate() ?: return
        val targetSink = resolveTargetSink(route, sinks)

        if (targetSink != null) {
            val sinkName = targetSink.name
            val isNewSink = currentProvider == null || activeSinkName != sinkName
            activeSinkName = sinkName
            val currentVol = targetSink.volume.coerceIn(0, 100)
            val currentStep = percentToStep(currentVol)
            val displayName = AudioRouteMapper.formatDisplayName(targetSink)

            if (isNewSink) {
                val provider = object : VolumeProviderCompat(VOLUME_CONTROL_ABSOLUTE, MAX_VOLUME_STEPS, currentStep) {
                    override fun onSetVolumeTo(volume: Int) {
                        val clampedStep = volume.coerceIn(0, MAX_VOLUME_STEPS)
                        currentVolume = clampedStep
                        AppBridgeState.setSinkVolume(sinkName, stepToPercent(clampedStep))
                    }
                    override fun onAdjustVolume(direction: Int) {
                        if (direction == 0) return
                        val newStep = (currentVolume + direction).coerceIn(0, MAX_VOLUME_STEPS)
                        currentVolume = newStep
                        AppBridgeState.setSinkVolume(sinkName, stepToPercent(newStep))
                    }
                }
                currentProvider = provider
                delegate.setPlaybackToRemote(provider)
                delegate.setSessionActive(true, displayName)
            } else {
                currentProvider?.currentVolume = currentStep
            }
            Log.i(TAG, "Bound remote volume slider for sink: $sinkName ($currentVol% -> step $currentStep)")
        } else {
            activeSinkName = null; currentProvider = null
            delegate.setPlaybackToLocal(AudioManager.STREAM_MUSIC)
            if (AppBridgeState.currentMediaState.value == null) delegate.setSessionActive(false, "")
            Log.i(TAG, "Restored local volume control (no sinks available)")
        }
    }

    fun resolveTargetSink(route: String?, sinks: List<LaptopAudioSink>): LaptopAudioSink? {
        if (sinks.isEmpty()) return null
        if (route != null && route != "phone" && route != "singularity_phone_sink") {
            return sinks.find { it.name == route } ?: sinks.find { it.isDefault } ?: sinks.firstOrNull()
        }
        return sinks.find { it.isDefault } ?: sinks.firstOrNull()
    }

    companion object {
        private const val TAG = "RemoteVolumeManager"
        const val MAX_VOLUME_STEPS = 20
        fun percentToStep(percent: Int): Int = ((percent + 2) / 5).coerceIn(0, MAX_VOLUME_STEPS)
        fun stepToPercent(step: Int): Int = (step * 5).coerceIn(0, 100)
    }
}
