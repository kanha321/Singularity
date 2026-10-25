package com.kanhaji.singularity.feature.files.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FsFolderSizeProgressTest {

    @Test
    fun formattedSize_zeroBytes_returnsZeroB() {
        val progress = FsFolderSizeProgress(bytes = 0L)
        assertThat(progress.formattedSize).isEqualTo("0 B")
    }

    @Test
    fun formattedSize_kilobytes_formatsCorrectly() {
        val progress = FsFolderSizeProgress(bytes = 2048L)
        assertThat(progress.formattedSize).isEqualTo("2.0 KB")
    }

    @Test
    fun formattedSize_megabytes_formatsCorrectly() {
        val progress = FsFolderSizeProgress(bytes = 52428800L)
        assertThat(progress.formattedSize).isEqualTo("50.0 MB")
    }

    @Test
    fun formattedSize_gigabytes_formatsCorrectly() {
        val progress = FsFolderSizeProgress(bytes = 2147483648L)
        assertThat(progress.formattedSize).isEqualTo("2.0 GB")
    }
}
