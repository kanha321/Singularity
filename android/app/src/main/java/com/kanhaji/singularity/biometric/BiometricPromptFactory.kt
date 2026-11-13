package com.kanhaji.singularity.biometric

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import android.util.Log
import com.kanhaji.singularity.service.AppBridgeService

class BiometricPromptFactory(
    private val activity: FragmentActivity,
    private val isUnlock: Boolean,
    private val powerAction: String?,
    private val onComplete: (Boolean) -> Unit
) {
    private val isPamAuth = !isUnlock && (powerAction == null)
    private var failedAttemptsCount = 0

    fun createPromptInfo(title: String, reason: String): BiometricPrompt.PromptInfo {
        return BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle("Fingerprint Verification")
            .setDescription(reason)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setNegativeButtonText("Cancel")
            .build()
    }

    fun createPrompt(): BiometricPrompt {
        val executor = ContextCompat.getMainExecutor(activity)
        return BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                Log.i(BiometricAuthActivity.TAG, "Native biometric authentication SUCCEEDED (failed attempts: $failedAttemptsCount)")

                var sigString: String? = null
                var counterVal = 0L
                val auth = com.kanhaji.singularity.service.bridge.AppBridgeState.currentAuthRequest.value
                val crypto = result.cryptoObject
                if (auth?.nonce != null && crypto != null) {
                    try {
                        val nonceBytes = android.util.Base64.decode(auth.nonce, android.util.Base64.NO_WRAP)
                        counterVal = com.kanhaji.singularity.security.CounterManager.nextCounter(activity)
                        val user = auth.reason.substringAfter(":", "").trim()
                        val canonical = com.kanhaji.singularity.security.CanonicalPayload.build(
                            nonce = nonceBytes,
                            timestampSecs = auth.timestampSecs,
                            counter = counterVal,
                            service = auth.authAction,
                            user = user
                        )
                        sigString = com.kanhaji.singularity.security.KeystoreManager.signData(crypto, canonical)
                    } catch (e: Exception) {
                        Log.w(BiometricAuthActivity.TAG, "Failed signing canonical challenge: ${e.message}")
                    }
                }

                if (isPamAuth) AppBridgeService.reportBiometricResult(true, null, failedAttemptsCount, false, sigString, counterVal)
                if (isUnlock) AppBridgeService.requestPcUnlock()
                powerAction?.let { AppBridgeService.requestPowerAction(it) }
                AppBridgeService.dismissPcLockedNotification(activity)
                onComplete(true)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                Log.w(BiometricAuthActivity.TAG, "Native biometric authentication ERROR [$errorCode]: $errString")
                val isLockout = errorCode == BiometricPrompt.ERROR_LOCKOUT || errorCode == BiometricPrompt.ERROR_LOCKOUT_PERMANENT
                if (isPamAuth && isLockout) {
                    AppBridgeService.reportBiometricProgress("sensor_lockout", failedAttemptsCount, errString.toString())
                }
                if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                    errorCode == BiometricPrompt.ERROR_CANCELED
                ) {
                    if (isPamAuth) {
                        Log.i(BiometricAuthActivity.TAG, "Prompt dismissed by user/system; keeping auth request active for app retry")
                        AppBridgeService.reportBiometricProgress("cancelled", failedAttemptsCount)
                        AppBridgeService.onBiometricPromptDismissed(activity)
                    } else if (isUnlock && AppBridgeService.pcLockState.value == "locked") {
                        AppBridgeService.showPcLockedNotification(activity)
                    }
                } else if (isPamAuth) {
                    AppBridgeService.reportBiometricResult(false, errString.toString(), failedAttemptsCount, isLockout)
                }
                onComplete(false)
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                failedAttemptsCount++
                Log.d(BiometricAuthActivity.TAG, "Fingerprint not recognized (attempt $failedAttemptsCount)")
                if (isPamAuth) AppBridgeService.reportBiometricProgress("finger_mismatch", failedAttemptsCount)
            }
        })
    }
}

