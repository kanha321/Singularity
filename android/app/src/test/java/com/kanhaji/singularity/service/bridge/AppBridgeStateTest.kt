package com.kanhaji.singularity.service.bridge

import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.Before
import org.junit.Test

class AppBridgeStateTest {

    @Before
    fun setUp() {
        AppBridgeState.resetPcDisconnected(null)
    }

    @Test
    fun updateLockState_whenCameraActiveTrue_updatesCameraStreamingAndMic() {
        val json = JSONObject().apply {
            put("action", "lock_state")
            put("locked", false)
            put("logged_in", true)
            put("camera_active", true)
            put("camera_has_mic", true)
            put("camera_lens", "ultrawide")
        }

        AppBridgeState.updateLockState(json)

        assertThat(AppBridgeState.isCameraStreaming.value).isTrue()
        assertThat(AppBridgeState.isCameraMicActive.value).isTrue()
        assertThat(AppBridgeState.selectedCameraLens.value).isEqualTo("ultrawide")
    }

    @Test
    fun updateLockState_whenCameraActiveFalse_resetsCameraStreamingAndMic() {
        AppBridgeState.isCameraStreaming.value = true
        AppBridgeState.isCameraMicActive.value = true

        val json = JSONObject().apply {
            put("action", "lock_state")
            put("locked", false)
            put("logged_in", true)
            put("camera_active", false)
            put("camera_has_mic", false)
        }

        AppBridgeState.updateLockState(json)

        assertThat(AppBridgeState.isCameraStreaming.value).isFalse()
        assertThat(AppBridgeState.isCameraMicActive.value).isFalse()
    }

    @Test
    fun updateLockState_whenCameraActiveWithoutExplicitMic_defaultsMicToActive() {
        val json = JSONObject().apply {
            put("action", "lock_state")
            put("locked", false)
            put("logged_in", true)
            put("camera_active", true)
        }

        AppBridgeState.updateLockState(json)

        assertThat(AppBridgeState.isCameraStreaming.value).isTrue()
        assertThat(AppBridgeState.isCameraMicActive.value).isTrue()
    }

    @Test
    fun resetPcDisconnected_resetsStreamingAndMic() {
        AppBridgeState.isCameraStreaming.value = true
        AppBridgeState.isCameraMicActive.value = true

        AppBridgeState.resetPcDisconnected(null)

        assertThat(AppBridgeState.isCameraStreaming.value).isFalse()
        assertThat(AppBridgeState.isCameraMicActive.value).isFalse()
    }

    @Test
    fun requestStartCamera_and_requestStopCamera_cycle() {
        AppBridgeState.requestStartCamera("front")

        assertThat(AppBridgeState.pendingCameraAction.get()).isEqualTo("start")
        assertThat(AppBridgeState.pendingCameraLens.get()).isEqualTo("front")
        assertThat(AppBridgeState.selectedCameraLens.value).isEqualTo("front")

        AppBridgeState.requestStopCamera()
        assertThat(AppBridgeState.pendingCameraAction.get()).isEqualTo("stop")
    }
}

