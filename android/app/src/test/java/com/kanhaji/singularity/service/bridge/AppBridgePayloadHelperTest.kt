package com.kanhaji.singularity.service.bridge

import com.google.common.truth.Truth.assertThat
import com.kanhaji.singularity.feature.media.model.MediaState
import org.json.JSONObject
import org.junit.Before
import org.junit.Test

class AppBridgePayloadHelperTest {

    @Before
    fun setUp() {
        AppBridgeState.resetPcDisconnected(null)
    }

    @Test
    fun parsePhoneMediaAction_validJson_extractsAllFields() {
        val json = JSONObject().apply {
            put("phone_media_action", JSONObject().apply {
                put("player", "com.google.android.youtube")
                put("command", "seek")
                put("position_ms", 45000L)
            })
        }

        val action = AppBridgePayloadHelper.parsePhoneMediaAction(json)

        assertThat(action).isNotNull()
        assertThat(action?.player).isEqualTo("com.google.android.youtube")
        assertThat(action?.command).isEqualTo("seek")
        assertThat(action?.positionMs).isEqualTo(45000L)
    }

    @Test
    fun parsePhoneMediaAction_missingOrEmptyCommand_returnsNull() {
        val jsonNoCmd = JSONObject().apply {
            put("phone_media_action", JSONObject().apply {
                put("player", "com.spotify.music")
                put("command", "  ")
            })
        }
        assertThat(AppBridgePayloadHelper.parsePhoneMediaAction(jsonNoCmd)).isNull()

        val jsonNoObject = JSONObject().apply {
            put("action", "lock_state")
        }
        assertThat(AppBridgePayloadHelper.parsePhoneMediaAction(jsonNoObject)).isNull()
    }

    @Test
    fun buildLockResponse_withPhoneMedia_serializesCorrectly() {
        val phoneState = MediaState(
            player = "com.spotify.music",
            title = "Blinding Lights",
            artist = "The Weeknd",
            album = "After Hours",
            artUrl = "https://example.com/cover.jpg",
            status = "Playing",
            positionMs = 12000L,
            durationMs = 200000L,
            canPlay = true,
            canPause = true,
            canNext = true,
            canPrev = true,
            canSeek = true
        )

        val resp = AppBridgePayloadHelper.buildLockResponse(
            locked = false,
            loggedIn = true,
            phoneMedia = phoneState,
            authSettingsJson = null
        )

        assertThat(resp.getString("status")).isEqualTo("ok")
        assertThat(resp.getBoolean("pc_locked")).isFalse()
        assertThat(resp.getBoolean("logged_in")).isTrue()
        assertThat(resp.has("phone_media")).isTrue()

        val phoneMediaJson = resp.getJSONObject("phone_media")
        assertThat(phoneMediaJson.getString("player")).isEqualTo("com.spotify.music")
        assertThat(phoneMediaJson.getString("title")).isEqualTo("Blinding Lights")
        assertThat(phoneMediaJson.getString("status")).isEqualTo("Playing")
        assertThat(phoneMediaJson.getLong("position_ms")).isEqualTo(12000L)
    }

    @Test
    fun buildLockResponse_withoutPhoneMedia_omitsPhoneMediaField() {
        val resp = AppBridgePayloadHelper.buildLockResponse(
            locked = true,
            loggedIn = false,
            phoneMedia = null,
            authSettingsJson = null
        )

        assertThat(resp.getBoolean("pc_locked")).isTrue()
        assertThat(resp.getBoolean("logged_in")).isFalse()
        assertThat(resp.has("phone_media")).isFalse()
    }
}

