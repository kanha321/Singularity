package com.kanhaji.singularity.feature.player.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlayerTimeUtilsTest {

    @Test
    fun formatTime_underOneHour_formatsMinutesAndSeconds() {
        assertThat(PlayerTimeUtils.formatTime(0L)).isEqualTo("00:00")
        assertThat(PlayerTimeUtils.formatTime(5_000L)).isEqualTo("00:05")
        assertThat(PlayerTimeUtils.formatTime(65_000L)).isEqualTo("01:05")
        assertThat(PlayerTimeUtils.formatTime(599_000L)).isEqualTo("09:59")
        assertThat(PlayerTimeUtils.formatTime(3_599_000L)).isEqualTo("59:59")
    }

    @Test
    fun formatTime_oneHourOrMore_formatsHoursMinutesAndSeconds() {
        assertThat(PlayerTimeUtils.formatTime(3_600_000L)).isEqualTo("01:00:00")
        assertThat(PlayerTimeUtils.formatTime(3_661_000L)).isEqualTo("01:01:01")
        assertThat(PlayerTimeUtils.formatTime(7_200_000L)).isEqualTo("02:00:00")
        assertThat(PlayerTimeUtils.formatTime(36_000_000L)).isEqualTo("10:00:00")
    }

    @Test
    fun formatTime_negativeOrZero_returnsZeroTime() {
        assertThat(PlayerTimeUtils.formatTime(-1000L)).isEqualTo("00:00")
    }
}
