package com.kanhaji.singularity.util

import android.content.Context
import android.content.ContextWrapper
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DeviceIdentityHelperTest {

    @Test
    fun getFriendlyDeviceName_fallbackOnNullContext_returnsNonEmptyString() {
        val fakeContext = object : ContextWrapper(null) {}
        val name = DeviceIdentityHelper.getFriendlyDeviceName(fakeContext)
        assertThat(name).isNotEmpty()
    }
}
