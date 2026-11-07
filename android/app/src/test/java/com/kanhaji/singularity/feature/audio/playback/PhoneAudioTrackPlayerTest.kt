package com.kanhaji.singularity.feature.audio.playback

import android.media.AudioFormat
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PhoneAudioTrackPlayerTest {

    @Test
    fun player_initializesWithExpectedAudioParameters() {
        val player = PhoneAudioTrackPlayer()
        assertThat(player.sampleRate).isEqualTo(48000)
        assertThat(player.channelConfig).isEqualTo(AudioFormat.CHANNEL_OUT_STEREO)
        assertThat(player.audioFormat).isEqualTo(AudioFormat.ENCODING_PCM_16BIT)
        assertThat(player.bufferSize).isAtLeast(4096)
        assertThat(player.isPlaying()).isFalse()
    }

    @Test
    fun write_whenUninitialized_returnsNegative() {
        val player = PhoneAudioTrackPlayer()
        val data = ByteArray(1024)
        val result = player.write(data, 0, data.size)
        assertThat(result).isEqualTo(-1)
    }

    @Test
    fun stopAndRelease_doNotThrow() {
        val player = PhoneAudioTrackPlayer()
        player.stop()
        player.release()
        assertThat(player.isPlaying()).isFalse()
    }
}

