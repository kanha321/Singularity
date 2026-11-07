package com.kanhaji.singularity.feature.audio.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AudioSliderHelperTest {

    @Test
    fun computeVolumePercent_normalCoordinates_computesAccuratePercentage() {
        val width = 1000f
        assertThat(AudioSliderHelper.computeVolumePercent(0f, width)).isEqualTo(0)
        assertThat(AudioSliderHelper.computeVolumePercent(250f, width)).isEqualTo(25)
        assertThat(AudioSliderHelper.computeVolumePercent(500f, width)).isEqualTo(50)
        assertThat(AudioSliderHelper.computeVolumePercent(750f, width)).isEqualTo(75)
        assertThat(AudioSliderHelper.computeVolumePercent(1000f, width)).isEqualTo(100)
    }

    @Test
    fun computeVolumePercent_outOfBounds_clampsBetweenZeroAndHundred() {
        val width = 500f
        assertThat(AudioSliderHelper.computeVolumePercent(-100f, width)).isEqualTo(0)
        assertThat(AudioSliderHelper.computeVolumePercent(800f, width)).isEqualTo(100)
    }

    @Test
    fun computeVolumePercent_zeroOrNegativeWidth_returnsZero() {
        assertThat(AudioSliderHelper.computeVolumePercent(100f, 0f)).isEqualTo(0)
        assertThat(AudioSliderHelper.computeVolumePercent(100f, -50f)).isEqualTo(0)
    }

    @Test
    fun formatVolumeSubtitle_muted_returnsMuted() {
        assertThat(AudioSliderHelper.formatVolumeSubtitle(80, true)).isEqualTo("Muted")
        assertThat(AudioSliderHelper.formatVolumeSubtitle(0, true)).isEqualTo("Muted")
    }

    @Test
    fun formatVolumeSubtitle_unmuted_returnsVolumePercent() {
        assertThat(AudioSliderHelper.formatVolumeSubtitle(65, false)).isEqualTo("65% volume")
        assertThat(AudioSliderHelper.formatVolumeSubtitle(0, false)).isEqualTo("0% volume")
        assertThat(AudioSliderHelper.formatVolumeSubtitle(100, false)).isEqualTo("100% volume")
    }
}

