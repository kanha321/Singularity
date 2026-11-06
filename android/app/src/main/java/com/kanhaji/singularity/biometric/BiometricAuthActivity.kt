package com.kanhaji.singularity.biometric

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import com.kanhaji.singularity.feature.auth.data.AuthSettingsManager
import com.kanhaji.singularity.feature.auth.model.AuthMode
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
                        try { act.biometricPrompt?.cancelAuthentication() } catch (e: Exception) { Log.w(TAG, "cancel: ${e.message}") }
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
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }

        val isUnlock = intent.getBooleanExtra(EXTRA_IS_UNLOCK, false)
        val isLogin = intent.getBooleanExtra(EXTRA_IS_LOGIN, false)
        val settings = AuthSettingsManager.settings.value

        if (isUnlock && settings.unlockMode == AuthMode.DENY) {
            Toast.makeText(this, "Unlocking with phone is restricted", Toast.LENGTH_SHORT).show()
            dismissAndFinish()
            return
        }
        if (isLogin && settings.loginMode == AuthMode.DENY) {
            Toast.makeText(this, "Logging in with phone is restricted", Toast.LENGTH_SHORT).show()
            dismissAndFinish()
            return
        }

        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Singularity Security"
        val reason = intent.getStringExtra(EXTRA_REASON) ?: "Host Authentication"
        launchBiometricPrompt(title, reason)
    }

    override fun onDestroy() {
        overridePendingTransition(0, 0)
        if (activeActivity.get() == this) activeActivity = java.lang.ref.WeakReference(null)
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
        if (isPromptActive) return
        isPromptActive = true

        val isUnlock = intent.getBooleanExtra(EXTRA_IS_UNLOCK, false)
        val isLogin = intent.getBooleanExtra(EXTRA_IS_LOGIN, false)
        val powerAction = intent.getStringExtra(EXTRA_POWER_ACTION)
        val isPamAuth = !isUnlock && !isLogin && (powerAction == null)

        val factory = BiometricPromptFactory(this, isUnlock, isLogin, powerAction) { success ->
            isPromptActive = false
            biometricPrompt = null
            setResult(if (success) Activity.RESULT_OK else Activity.RESULT_CANCELED)
            dismissAndFinish()
        }

        val prompt = factory.createPrompt()
        this.biometricPrompt = prompt
        prompt.authenticate(factory.createPromptInfo(title, reason))
        if (isPamAuth) AppBridgeService.reportBiometricProgress("auth_ready", 0)
    }
}
