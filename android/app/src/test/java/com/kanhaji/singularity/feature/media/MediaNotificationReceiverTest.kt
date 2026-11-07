package com.kanhaji.singularity.feature.media

import com.google.common.truth.Truth.assertThat
import com.kanhaji.singularity.feature.audio.model.LaptopAudioSink
import com.kanhaji.singularity.service.bridge.AppBridgeState
import org.junit.After
import org.junit.Before
import org.junit.Test

class MediaNotificationReceiverTest {

    private lateinit var receiver: MediaNotificationReceiver

    @Before
    fun setUp() {
        receiver = MediaNotificationReceiver()
        AppBridgeState.resetPcDisconnected(null)
        AppBridgeState.availableLaptopAudioSinks.value = emptyList()
        AppBridgeState.pendingAudioRouteCommand.set(null)
    }

    @After
    fun tearDown() {
        AppBridgeState.resetPcDisconnected(null)
        AppBridgeState.availableLaptopAudioSinks.value = emptyList()
        AppBridgeState.pendingAudioRouteCommand.set(null)
    }

    @Test
    fun processAction_actionVolumeUp_stepsVolumeByFive() {
        val sink = LaptopAudioSink("alsa_output.hdmi", "Odyssey G5", "Odyssey G5", "hdmi", true, 80, false)
        AppBridgeState.availableLaptopAudioSinks.value = listOf(sink)

        receiver.processAction(MediaNotificationReceiver.ACTION_VOLUME_UP)

        val cmd = AppBridgeState.pendingAudioRouteCommand.get()
        assertThat(cmd).isNotNull()
        assertThat(cmd!!.getString("action")).isEqualTo("set_volume")
        assertThat(cmd.getString("target_sink")).isEqualTo("alsa_output.hdmi")
        assertThat(cmd.getInt("volume")).isEqualTo(85)
    }

    @Test
    fun processAction_actionVolumeDown_stepsVolumeByFive() {
        val sink = LaptopAudioSink("alsa_output.hdmi", "Odyssey G5", "Odyssey G5", "hdmi", true, 80, false)
        AppBridgeState.availableLaptopAudioSinks.value = listOf(sink)

        receiver.processAction(MediaNotificationReceiver.ACTION_VOLUME_DOWN)

        val cmd = AppBridgeState.pendingAudioRouteCommand.get()
        assertThat(cmd).isNotNull()
        assertThat(cmd!!.getString("action")).isEqualTo("set_volume")
        assertThat(cmd.getString("target_sink")).isEqualTo("alsa_output.hdmi")
        assertThat(cmd.getInt("volume")).isEqualTo(75)
    }

    @Test
    fun processAction_actionToggleMute_invertsMute() {
        val sink = LaptopAudioSink("alsa_output.hdmi", "Odyssey G5", "Odyssey G5", "hdmi", true, 80, false)
        AppBridgeState.availableLaptopAudioSinks.value = listOf(sink)

        receiver.processAction(MediaNotificationReceiver.ACTION_TOGGLE_MUTE)

        val cmd = AppBridgeState.pendingAudioRouteCommand.get()
        assertThat(cmd).isNotNull()
        assertThat(cmd!!.getString("action")).isEqualTo("set_mute")
        assertThat(cmd.getString("target_sink")).isEqualTo("alsa_output.hdmi")
        assertThat(cmd.getBoolean("mute")).isTrue()
    }

    @Test
    fun processAction_noSinks_gracefulNoOp() {
        AppBridgeState.availableLaptopAudioSinks.value = emptyList()

        receiver.processAction(MediaNotificationReceiver.ACTION_VOLUME_UP)

        assertThat(AppBridgeState.pendingAudioRouteCommand.get()).isNull()
    }
}
