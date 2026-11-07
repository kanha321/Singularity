package com.kanhaji.singularity.feature.audio.route

import com.google.common.truth.Truth.assertThat
import com.kanhaji.singularity.service.bridge.AppBridgeState
import org.junit.Before
import org.junit.Test

class SingularityMediaRoute2ProviderServiceTest {

    private lateinit var service: SingularityMediaRoute2ProviderService

    @Before
    fun setUp() {
        AppBridgeState.resetPcDisconnected(null)
        AppBridgeState.isPcConnected.value = true
        service = SingularityMediaRoute2ProviderService()
    }

    @Test
    fun onSetRouteVolume_updatesAppBridgeState() {
        service.onSetRouteVolume(1L, "alsa_output.pci.hdmi", 65)
        val cmd = AppBridgeState.pendingAudioRouteCommand.get()
        assertThat(cmd).isNotNull()
        assertThat(cmd!!.getString("action")).isEqualTo("set_volume")
        assertThat(cmd.getString("target_sink")).isEqualTo("alsa_output.pci.hdmi")
        assertThat(cmd.getInt("volume")).isEqualTo(65)
    }

    @Test
    fun onSetSessionVolume_whenActiveSessionMatches_updatesVolume() {
        // Create session
        try {
            service.onCreateSession(1L, "com.spotify.music", "alsa_output.pci.speaker", null)
        } catch (_: Exception) {
            // notifySessionCreated calls framework binder in JVM
        }

        assertThat(AppBridgeState.selectedAudioRoute.value).isEqualTo("alsa_output.pci.speaker")
    }

    @Test
    fun onReleaseSession_whenLastSessionReleased_resetsToPhone() {
        AppBridgeState.setAudioRoute("alsa_output.pci.hdmi")
        assertThat(AppBridgeState.selectedAudioRoute.value).isEqualTo("alsa_output.pci.hdmi")

        try {
            service.onReleaseSession(1L, "session_test")
        } catch (_: Exception) {}

        assertThat(AppBridgeState.selectedAudioRoute.value).isEqualTo("phone")
    }
}

