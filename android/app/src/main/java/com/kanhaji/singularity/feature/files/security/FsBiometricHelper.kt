package com.kanhaji.singularity.feature.files.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.kanhaji.singularity.ui.components.AppSnackbar

object FsBiometricHelper {

    fun promptSudoElevation(
        context: Context,
        title: String = "Singularity Sudo Mode",
        subtitle: String = "Verify fingerprint to unlock root access",
        onSuccess: () -> Unit,
        onFailed: () -> Unit = {}
    ) {
        val activity = context as? FragmentActivity ?: run {
            AppSnackbar.show("Authentication error: invalid activity context")
            onFailed()
            return
        }

        val biometricManager = BiometricManager.from(context)
        val canAuth = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )

        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
            AppSnackbar.show("Biometrics not configured on this device")
            onFailed()
            return
        }

        val executor = ContextCompat.getMainExecutor(context)
        val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                FsSudoSessionManager.elevate()
                AppSnackbar.show("🛡️ Sudo Mode Activated")
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    AppSnackbar.show("Authentication error: $errString")
                }
                onFailed()
            }

            override fun onAuthenticationFailed() {
                AppSnackbar.show("Fingerprint not recognized")
                onFailed()
            }
        })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        prompt.authenticate(promptInfo)
    }
}
