package com.kanhaji.singularity.feature.media.session

import android.os.Build
import android.provider.Settings
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PhoneMediaPermissionHelperTest {

    @Test
    fun getSettingsAction_resolvesTargetActionBasedOnSdk() {
        val actionR = PhoneMediaPermissionHelper.getSettingsAction(Build.VERSION_CODES.R)
        assertThat(actionR).isEqualTo(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)

        val actionLegacy = PhoneMediaPermissionHelper.getSettingsAction(Build.VERSION_CODES.Q)
        assertThat(actionLegacy).isEqualTo(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    }

    @Test
    fun createSettingsIntent_doesNotThrow() {
        val mockContext = object : android.content.ContextWrapper(null) {
            override fun getPackageName(): String = "com.kanhaji.singularity"
        }

        val intent = PhoneMediaPermissionHelper.createSettingsIntent(mockContext, sdkInt = 30)
        assertThat(intent).isNotNull()
    }
}
