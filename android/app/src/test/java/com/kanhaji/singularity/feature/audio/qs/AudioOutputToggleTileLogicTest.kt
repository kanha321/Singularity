package com.kanhaji.singularity.feature.audio.qs

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AudioOutputToggleTileLogicTest {

    @Test
    fun resolveNextRoute_whenOnPhone_switchesToDefaultLaptopSink() {
        val next = AudioOutputToggleLogic.resolveNextRoute("phone", "alsa_output.speaker")
        assertThat(next).isEqualTo("alsa_output.speaker")
    }

    @Test
    fun resolveNextRoute_whenNull_switchesToDefaultLaptopSink() {
        val next = AudioOutputToggleLogic.resolveNextRoute(null, "alsa_output.speaker")
        assertThat(next).isEqualTo("alsa_output.speaker")
    }

    @Test
    fun resolveNextRoute_whenOnLaptop_switchesToPhone() {
        val next = AudioOutputToggleLogic.resolveNextRoute("alsa_output.speaker", "alsa_output.speaker")
        assertThat(next).isEqualTo("phone")
    }

    @Test
    fun resolveNextRoute_whenOnSecondaryLaptopSink_switchesToPhone() {
        val next = AudioOutputToggleLogic.resolveNextRoute("alsa_output.hdmi", "alsa_output.speaker")
        assertThat(next).isEqualTo("phone")
    }

    @Test
    fun resolveNextRoute_whenNoDefaultSink_returnsNull() {
        val next = AudioOutputToggleLogic.resolveNextRoute("phone", null)
        assertThat(next).isNull()
    }

    @Test
    fun isRoutingToLaptop_identifiesRoutesCorrectly() {
        assertThat(AudioOutputToggleLogic.isRoutingToLaptop("phone")).isFalse()
        assertThat(AudioOutputToggleLogic.isRoutingToLaptop(null)).isFalse()
        assertThat(AudioOutputToggleLogic.isRoutingToLaptop("alsa_output.speaker")).isTrue()
        assertThat(AudioOutputToggleLogic.isRoutingToLaptop("alsa_output.hdmi")).isTrue()
    }

    @Test
    fun formatSubtitle_displaysCorrectLabels() {
        assertThat(AudioOutputToggleLogic.formatSubtitle(false, "Speakers")).isEqualTo("Phone Speaker")
        assertThat(AudioOutputToggleLogic.formatSubtitle(true, "Speakers")).isEqualTo("Laptop: Speakers")
        assertThat(AudioOutputToggleLogic.formatSubtitle(true, null)).isEqualTo("Laptop Speaker")
        assertThat(AudioOutputToggleLogic.formatSubtitle(true, "")).isEqualTo("Laptop Speaker")
    }
}

