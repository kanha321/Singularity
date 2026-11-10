package com.kanhaji.singularity.service.bridge

import com.google.common.truth.Truth.assertThat
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Before
import org.junit.Test

class AppBridgeAudioPayloadTest {

    @Before
    fun setUp() {
        AppBridgeState.resetPcDisconnected(null)
    }

    @Test
    fun parseAudioSinks_validJsonArray_returnsParsedList() {
        val json = JSONObject().apply {
            put("audio_sinks", JSONArray().apply {
                put(JSONObject().apply {
                    put("name", "alsa_output.hdmi")
                    put("description", "AD107 [Odyssey G5]")
                    put("nick", "Odyssey G5")
                    put("device_type", "hdmi")
                    put("is_default", true)
                    put("volume", 100)
                    put("mute", false)
                })
            })
        }
        val sinks = AppBridgePayloadHelper.parseAudioSinks(json)
        assertThat(sinks).isNotNull()
        assertThat(sinks).hasSize(1)
        assertThat(sinks!![0].nick).isEqualTo("Odyssey G5")
        assertThat(sinks[0].isDefault).isTrue()
    }

    @Test
    fun parseActiveAudioRoute_validJson_returnsParsedInfo() {
        val json = JSONObject().apply {
            put("active_audio_route", JSONObject().apply {
                put("output_device_name", "alsa_output.hdmi")
                put("output_device_label", "Laptop: Odyssey G5")
                put("source_app", "Thorium")
                put("source_origin", "laptop")
                put("is_playing", true)
            })
        }
        val route = AppBridgePayloadHelper.parseActiveAudioRoute(json)
        assertThat(route).isNotNull()
        assertThat(route!!.outputDeviceLabel).isEqualTo("Laptop: Odyssey G5")
        assertThat(route.sourceApp).isEqualTo("Thorium")
        assertThat(route.isPlaying).isTrue()
    }

    @Test
    fun buildLockResponse_withPendingAudioRoute_includesCommandAndClears() {
        AppBridgeState.setAudioRoute("alsa_output.hdmi")
        assertThat(AppBridgeState.selectedAudioRoute.value).isEqualTo("alsa_output.hdmi")

        val resp = AppBridgePayloadHelper.buildLockResponse(locked = false, loggedIn = true)
        assertThat(resp.has("audio_route")).isTrue()

        val routeObj = resp.getJSONObject("audio_route")
        assertThat(routeObj.getString("action")).isEqualTo("set_route")
        assertThat(routeObj.getString("target_sink")).isEqualTo("alsa_output.hdmi")
        assertThat(routeObj.has("low_latency")).isTrue()

        val resp2 = AppBridgePayloadHelper.buildLockResponse(locked = false, loggedIn = true)
        assertThat(resp2.has("audio_route")).isFalse()
    }

    @Test
    fun setSinkVolumeAndMute_setsPendingCommands() {
        AppBridgeState.setSinkVolume("alsa_output.speaker", 75)
        var resp = AppBridgePayloadHelper.buildLockResponse(locked = false, loggedIn = true)
        assertThat(resp.getJSONObject("audio_route").getInt("volume")).isEqualTo(75)

        AppBridgeState.setSinkMute("alsa_output.speaker", true)
        resp = AppBridgePayloadHelper.buildLockResponse(locked = false, loggedIn = true)
        assertThat(resp.getJSONObject("audio_route").getBoolean("mute")).isTrue()
    }

    @Test
    fun setLaptopToPhoneAudio_setsMutualExclusionState() {
        AppBridgeState.setLaptopToPhoneAudio(true)
        assertThat(AppBridgeState.isListeningToLaptop.value).isTrue()
        assertThat(AppBridgeState.selectedAudioRoute.value).isEqualTo("singularity_phone_sink")

        val resp = AppBridgePayloadHelper.buildLockResponse(locked = false, loggedIn = true)
        assertThat(resp.has("audio_route")).isTrue()
        val routeObj = resp.getJSONObject("audio_route")
        assertThat(routeObj.getString("action")).isEqualTo("set_route")
        assertThat(routeObj.getString("target_sink")).isEqualTo("singularity_phone_sink")
        assertThat(routeObj.getBoolean("laptop_to_phone")).isTrue()
    }

    @Test
    fun resetPcDisconnected_clearsAllAudioState() {
        AppBridgeState.isPcConnected.value = true
        AppBridgeState.setAudioRoute("alsa_output.hdmi")
        AppBridgeState.resetPcDisconnected(null)

        assertThat(AppBridgeState.selectedAudioRoute.value).isEqualTo("phone")
        assertThat(AppBridgeState.availableLaptopAudioSinks.value).isEmpty()
        assertThat(AppBridgeState.activeAudioRoute.value).isNull()
        assertThat(AppBridgeState.pendingAudioRouteCommand.get()).isNull()
    }

    @Test
    fun setLowLatencyMode_setsPendingAudioRouteCommand() {
        com.kanhaji.singularity.feature.audio.route.AudioRoutePreferences.setLowLatencyMode(true)
        val resp = AppBridgePayloadHelper.buildLockResponse(locked = false, loggedIn = true)
        assertThat(resp.has("audio_route")).isTrue()
        val routeObj = resp.getJSONObject("audio_route")
        assertThat(routeObj.getString("action")).isEqualTo("set_low_latency")
        assertThat(routeObj.getBoolean("low_latency")).isTrue()
    }
}
