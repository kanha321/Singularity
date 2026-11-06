package com.kanhaji.singularity.feature.media.model

import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.Test

class MediaStateTest {

    @Test
    fun toJson_serializesAllFieldsCorrectly() {
        val state = MediaState(
            player = "com.spotify.music",
            title = "Starboy",
            artist = "The Weeknd",
            album = "Starboy",
            artUrl = "https://example.com/art.jpg",
            status = "Playing",
            positionMs = 30000L,
            durationMs = 230000L,
            canPlay = true,
            canPause = true,
            canNext = true,
            canPrev = true,
            canSeek = true
        )

        val json = state.toJson()

        assertThat(json.getString("player")).isEqualTo("com.spotify.music")
        assertThat(json.getString("title")).isEqualTo("Starboy")
        assertThat(json.getString("artist")).isEqualTo("The Weeknd")
        assertThat(json.getString("album")).isEqualTo("Starboy")
        assertThat(json.getString("art_url")).isEqualTo("https://example.com/art.jpg")
        assertThat(json.getString("status")).isEqualTo("Playing")
        assertThat(json.getLong("position_ms")).isEqualTo(30000L)
        assertThat(json.getLong("duration_ms")).isEqualTo(230000L)
        assertThat(json.getBoolean("can_play")).isTrue()
        assertThat(json.getBoolean("can_pause")).isTrue()
        assertThat(json.getBoolean("can_next")).isTrue()
        assertThat(json.getBoolean("can_prev")).isTrue()
        assertThat(json.getBoolean("can_seek")).isTrue()
    }

    @Test
    fun roundTrip_preservesAllData() {
        val original = MediaState(
            player = "com.google.android.youtube",
            title = "Video Title",
            artist = "Channel",
            album = "Playlist",
            artUrl = null,
            status = "Paused",
            positionMs = 15000L,
            durationMs = 60000L,
            canPlay = true,
            canPause = false,
            canNext = false,
            canPrev = true,
            canSeek = true
        )

        val json = original.toJson()
        val restored = MediaState.fromJson(json)

        assertThat(restored).isEqualTo(original)
        assertThat(restored.isPlaying).isFalse()
    }

    @Test
    fun isPlaying_returnsTrueForVariousCasings() {
        assertThat(createWithStatus("Playing").isPlaying).isTrue()
        assertThat(createWithStatus("playing").isPlaying).isTrue()
        assertThat(createWithStatus("PLAYING").isPlaying).isTrue()
        assertThat(createWithStatus("Paused").isPlaying).isFalse()
        assertThat(createWithStatus("Stopped").isPlaying).isFalse()
    }

    @Test
    fun fromJson_handlesMissingFieldsGracefully() {
        val emptyJson = JSONObject()
        val state = MediaState.fromJson(emptyJson)

        assertThat(state.player).isEmpty()
        assertThat(state.title).isEmpty()
        assertThat(state.artist).isEmpty()
        assertThat(state.album).isEmpty()
        assertThat(state.artUrl).isNull()
        assertThat(state.status).isEqualTo("Stopped")
        assertThat(state.positionMs).isEqualTo(0L)
        assertThat(state.durationMs).isEqualTo(0L)
        assertThat(state.canPlay).isTrue()
        assertThat(state.canPause).isTrue()
        assertThat(state.canNext).isFalse()
        assertThat(state.canPrev).isFalse()
        assertThat(state.canSeek).isFalse()
    }

    @Test
    fun roundTrip_preservesArtBase64() {
        val state = createWithStatus("Playing").copy(artBase64 = "iVBORw0KGgoAAAANSUhEUg==")
        val json = state.toJson()
        assertThat(json.getString("art_base64")).isEqualTo("iVBORw0KGgoAAAANSUhEUg==")
        val restored = MediaState.fromJson(json)
        assertThat(restored.artBase64).isEqualTo("iVBORw0KGgoAAAANSUhEUg==")
    }

    private fun createWithStatus(status: String) = MediaState(
        player = "p", title = "t", artist = "a", album = "al", artUrl = null,
        status = status, positionMs = 0L, durationMs = 0L, canPlay = true,
        canPause = true, canNext = false, canPrev = false, canSeek = false
    )
}

