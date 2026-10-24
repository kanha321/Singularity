package com.kanhaji.singularity.biometric

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.kanhaji.singularity.service.AppBridgeService

class BiometricAuthActivity : FragmentActivity() {

    companion object {
        const val TAG = "SingularityBiometric"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_REASON = "extra_reason"
        const val EXTRA_IS_UNLOCK = "extra_is_unlock"
        const val EXTRA_IS_LOGIN = "extra_is_login"
        const val EXTRA_POWER_ACTION = "extra_power_action"

        private var activeActivity = java.lang.ref.WeakReference<BiometricAuthActivity?>(null)

        fun dismissActivePrompt() {
            activeActivity.get()?.let { act ->
                act.runOnUiThread {
                    if (!act.isFinishing && !act.isDestroyed) {
                        Log.i(TAG, "Dismissing active biometric prompt per host cancel request")
                        try {
                            act.biometricPrompt?.cancelAuthentication()
                        } catch (e: Exception) {
                            Log.w(TAG, "cancelAuthentication error: ${e.message}")
                        }
                        act.dismissAndFinish()
                    }
                }
            }
        }
    }

    private var isPromptActive = false
    private var biometricPrompt: BiometricPrompt? = null

    private fun dismissAndFinish() {
        finishAndRemoveTask()
        overridePendingTransition(0, 0)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)
        window.setBackgroundDrawableResource(android.R.color.transparent)
        window.setDimAmount(0f)
        activeActivity = java.lang.ref.WeakReference(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        val isUnlock = intent.getBooleanExtra(EXTRA_IS_UNLOCK, false)
        val isLogin = intent.getBooleanExtra(EXTRA_IS_LOGIN, false)
        val settings = com.kanhaji.singularity.feature.auth.data.AuthSettingsManager.settings.value

        if (isUnlock && settings.unlockMode == com.kanhaji.singularity.feature.auth.model.AuthMode.DENY) {
            android.widget.Toast.makeText(this, "Unlocking with phone is restricted", android.widget.Toast.LENGTH_SHORT).show()
            dismissAndFinish()
            return
        }
        if (isLogin && settings.loginMode == com.kanhaji.singularity.feature.auth.model.AuthMode.DENY) {
            android.widget.Toast.makeText(this, "Logging in with phone is restricted", android.widget.Toast.LENGTH_SHORT).show()
            dismissAndFinish()
            return
        }

        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Singularity Security"
        val reason = intent.getStringExtra(EXTRA_REASON) ?: "Host Authentication"

        launchBiometricPrompt(title, reason)
    }

    override fun onDestroy() {
        overridePendingTransition(0, 0)
        if (activeActivity.get() == this) {
            activeActivity = java.lang.ref.WeakReference(null)
        }
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (!isPromptActive) {
            val title = intent.getStringExtra(EXTRA_TITLE) ?: "Singularity Security"
            val reason = intent.getStringExtra(EXTRA_REASON) ?: "Host Authentication"
            launchBiometricPrompt(title, reason)
        }
    }

    private fun launchBiometricPrompt(title: String, reason: String) {
        if (isPromptActive) {
            Log.d(TAG, "Biometric prompt is already active, ignoring duplicate launch")
            return
        }
        isPromptActive = true

        val executor = ContextCompat.getMainExecutor(this)

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle("Fingerprint Verification")
            .setDescription(reason)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setNegativeButtonText("Cancel")
            .build()

        var failedAttemptsCount = 0
        val isUnlock = intent.getBooleanExtra(EXTRA_IS_UNLOCK, false)
        val isLogin = intent.getBooleanExtra(EXTRA_IS_LOGIN, false)
        val powerAction = intent.getStringExtra(EXTRA_POWER_ACTION)
        val isPamAuth = !isUnlock && !isLogin && (powerAction == null)

        val prompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    isPromptActive = false
                    biometricPrompt = null
                    Log.i(TAG, "Native biometric authentication SUCCEEDED (failed attempts: $failedAttemptsCount)")
                    if (isPamAuth) {
                        AppBridgeService.reportBiometricResult(true, null, failedAttemptsCount)
                    }
                    if (isUnlock) {
                        AppBridgeService.requestPcUnlock()
                    }
                    if (isLogin) {
                        AppBridgeService.requestPcLogin()
                    }
                    powerAction?.let { action ->
                        AppBridgeService.requestPowerAction(action)
                    }
                    AppBridgeService.dismissPcLockedNotification(this@BiometricAuthActivity)
                    setResult(Activity.RESULT_OK)
                    dismissAndFinish()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    isPromptActive = false
                    biometricPrompt = null
                    Log.w(TAG, "Native biometric authentication ERROR [$errorCode]: $errString")
                    val isLockout = errorCode == BiometricPrompt.ERROR_LOCKOUT || errorCode == BiometricPrompt.ERROR_LOCKOUT_PERMANENT
                    if (isPamAuth && isLockout) {
                        AppBridgeService.reportBiometricProgress("sensor_lockout", failedAttemptsCount, errString.toString())
                    }
                    if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        errorCode == BiometricPrompt.ERROR_CANCELED
                    ) {
                        if (isPamAuth) {
                            Log.i(TAG, "Prompt dismissed by user/system; keeping auth request active for app retry")
                            AppBridgeService.reportBiometricProgress("cancelled", failedAttemptsCount)
                            AppBridgeService.onBiometricPromptDismissed(this@BiometricAuthActivity)
                        } else if (isUnlock) {
                            AppBridgeService.showPcLockedNotification(this@BiometricAuthActivity)
                        }
                    } else if (isPamAuth) {
                        AppBridgeService.reportBiometricResult(false, errString.toString(), failedAttemptsCount, isLockout)
                    }
                    setResult(Activity.RESULT_CANCELED)
                    dismissAndFinish()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    failedAttemptsCount++
                    Log.d(TAG, "Fingerprint not recognized (attempt $failedAttemptsCount)")
                    if (isPamAuth) {
                        AppBridgeService.reportBiometricProgress("finger_mismatch", failedAttemptsCount)
                    }
                }
            }
        )

        this.biometricPrompt = prompt
        prompt.authenticate(promptInfo)
        if (isPamAuth) {
            AppBridgeService.reportBiometricProgress("auth_ready", 0)
        }
    }
}
