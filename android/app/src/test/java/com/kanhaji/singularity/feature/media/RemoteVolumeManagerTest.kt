package com.kanhaji.singularity.feature.media

import android.media.AudioManager
import androidx.media.VolumeProviderCompat
import com.google.common.truth.Truth.assertThat
import com.kanhaji.singularity.feature.audio.model.LaptopAudioSink
import com.kanhaji.singularity.service.bridge.AppBridgeState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class FakeSessionVolumeDelegate : SessionVolumeDelegate {
    var remoteProvider: VolumeProviderCompat? = null
    var localStream: Int? = null
    var isActive: Boolean = false
    var activeTitle: String = ""

    override fun setPlaybackToRemote(volumeProvider: VolumeProviderCompat) {
        remoteProvider = volumeProvider; localStream = null
    }

    override fun setPlaybackToLocal(stream: Int) {
        localStream = stream; remoteProvider = null
    }

    override fun setSessionActive(active: Boolean, title: String) {
        isActive = active; activeTitle = title
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteVolumeManagerTest {
    private lateinit var fakeDelegate: FakeSessionVolumeDelegate
    private lateinit var manager: RemoteVolumeManager
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setUp() {
        AppBridgeState.resetPcDisconnected(null)
        AppBridgeState.availableLaptopAudioSinks.value = emptyList()
        AppBridgeState.selectedAudioRoute.value = "phone"
        AppBridgeState.pendingAudioRouteCommand.set(null)
        fakeDelegate = FakeSessionVolumeDelegate()
        manager = RemoteVolumeManager({ fakeDelegate }, testScope)
        manager.initialize()
    }

    @Test
    fun handleRouteChanged_phoneRouteWithDefaultSink_bindsDefaultRemoteSlider() = runTest {
        val sinks = listOf(
            LaptopAudioSink("alsa_output.hdmi", "Odyssey G5", "Odyssey G5", "hdmi", true, 75, false),
            LaptopAudioSink("alsa_output.speaker", "Speaker", "Speaker", "speaker", false, 90, false)
        )
        manager.updateVolumeBinding("phone", sinks)

        assertThat(fakeDelegate.remoteProvider).isNotNull()
        assertThat(fakeDelegate.remoteProvider?.currentVolume).isEqualTo(15)
        assertThat(fakeDelegate.isActive).isTrue()
        assertThat(fakeDelegate.activeTitle).contains("Odyssey G5")
    }

    @Test
    fun handleRouteChanged_explicitSinkRoute_bindsTargetSinkSlider() = runTest {
        val sinks = listOf(
            LaptopAudioSink("alsa_output.hdmi", "Odyssey G5", "Odyssey G5", "hdmi", false, 80, false),
            LaptopAudioSink("alsa_output.speaker", "Laptop Speakers", "Laptop Speakers", "speaker", true, 60, false)
        )
        manager.updateVolumeBinding("alsa_output.hdmi", sinks)

        assertThat(fakeDelegate.remoteProvider).isNotNull()
        assertThat(manager.activeSinkName).isEqualTo("alsa_output.hdmi")
        assertThat(fakeDelegate.remoteProvider?.currentVolume).isEqualTo(16)
        assertThat(fakeDelegate.activeTitle).contains("Odyssey G5")
    }

    @Test
    fun handleRouteChanged_noSinks_restoresLocalVolume() = runTest {
        manager.updateVolumeBinding("phone", emptyList())

        assertThat(fakeDelegate.remoteProvider).isNull()
        assertThat(fakeDelegate.localStream).isEqualTo(AudioManager.STREAM_MUSIC)
    }

    @Test
    fun volumeProvider_adjustVolume_sendsSinkVolumeCommand() = runTest {
        val sinks = listOf(
            LaptopAudioSink("alsa_output.hdmi", "Odyssey G5", "Odyssey G5", "hdmi", true, 80, false)
        )
        manager.updateVolumeBinding("phone", sinks)
        val provider = fakeDelegate.remoteProvider
        assertThat(provider).isNotNull()

        provider!!.onAdjustVolume(1)
        assertThat(provider.currentVolume).isEqualTo(17)
        val cmd = AppBridgeState.pendingAudioRouteCommand.get()
        assertThat(cmd).isNotNull()
        assertThat(cmd!!.getInt("volume")).isEqualTo(85)

        provider.onSetVolumeTo(12)
        assertThat(provider.currentVolume).isEqualTo(12)
        val cmdSet = AppBridgeState.pendingAudioRouteCommand.get()
        assertThat(cmdSet!!.getInt("volume")).isEqualTo(60)
    }

    @Test
    fun percentToStep_and_stepToPercent_scaleAccurately() {
        assertThat(RemoteVolumeManager.percentToStep(0)).isEqualTo(0)
        assertThat(RemoteVolumeManager.percentToStep(50)).isEqualTo(10)
        assertThat(RemoteVolumeManager.percentToStep(100)).isEqualTo(20)
        assertThat(RemoteVolumeManager.stepToPercent(0)).isEqualTo(0)
        assertThat(RemoteVolumeManager.stepToPercent(10)).isEqualTo(50)
        assertThat(RemoteVolumeManager.stepToPercent(20)).isEqualTo(100)
    }
}
