package com.kanhaji.singularity.feature.home.util

import android.content.Context
import android.content.Intent
import com.kanhaji.singularity.biometric.BiometricAuthActivity
import com.kanhaji.singularity.ui.components.AppSnackbar

object HomeBiometricLauncher {
    fun launchUnlock(context: Context, onNotice: (String) -> Unit = { AppSnackbar.show(it) }) {
        val settings = com.kanhaji.singularity.feature.auth.data.AuthSettingsManager.settings.value
        if (settings.isSnoozed) {
            onNotice("Global snooze is active")
            return
        }
        if (settings.unlockMode == com.kanhaji.singularity.feature.auth.model.AuthMode.DENY) {
            onNotice("Unlocking with phone is restricted")
            return
        }

        val intent = Intent(context, BiometricAuthActivity::class.java).apply {
            putExtra(BiometricAuthActivity.EXTRA_TITLE, "Singularity Lockscreen")
            putExtra(BiometricAuthActivity.EXTRA_REASON, "Unlock PC Screen")
            putExtra(BiometricAuthActivity.EXTRA_IS_UNLOCK, true)
        }
        context.startActivity(intent)
    }

    fun launchLogin(context: Context, onNotice: (String) -> Unit = { AppSnackbar.show(it) }) {
        val settings = com.kanhaji.singularity.feature.auth.data.AuthSettingsManager.settings.value
        if (settings.isSnoozed) {
            onNotice("Global snooze is active")
            return
        }
        if (settings.loginMode == com.kanhaji.singularity.feature.auth.model.AuthMode.DENY) {
            onNotice("Logging in with phone is restricted")
            return
        }

        val intent = Intent(context, BiometricAuthActivity::class.java).apply {
            putExtra(BiometricAuthActivity.EXTRA_TITLE, "Singularity Greeter")
            putExtra(BiometricAuthActivity.EXTRA_REASON, "Log In to PC Session")
            putExtra(BiometricAuthActivity.EXTRA_IS_LOGIN, true)
        }
        context.startActivity(intent)
    }

    fun launch(context: Context, isUnlock: Boolean) {
        if (isUnlock) {
            launchUnlock(context)
        } else {
            val intent = Intent(context, BiometricAuthActivity::class.java).apply {
                putExtra(BiometricAuthActivity.EXTRA_TITLE, "Singularity Security")
                putExtra(BiometricAuthActivity.EXTRA_REASON, "Test Fingerprint Verification")
            }
            context.startActivity(intent)
        }
    }
}
