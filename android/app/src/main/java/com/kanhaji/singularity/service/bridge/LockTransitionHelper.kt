package com.kanhaji.singularity.service.bridge

import android.content.Context
import android.content.Intent
import android.util.Log
import com.kanhaji.singularity.biometric.BiometricAuthActivity
import com.kanhaji.singularity.feature.auth.data.AuthSettingsManager
import com.kanhaji.singularity.feature.auth.model.AuthMode
import com.kanhaji.singularity.feature.auth.model.AuthSettings

object LockTransitionHelper {
    private const val TAG = "SingularityLockTransition"
    var lastLockedState: Boolean? = null
        internal set
    var lastLoggedInState: Boolean? = null
        internal set

    internal var onNotifyAction: ((Context, Boolean) -> Unit)? = null
    internal var onPromptAction: ((Context, Boolean) -> Unit)? = null
    internal var onDismissAction: ((Context) -> Unit)? = null

    fun handleLockTransition(
        context: Context,
        locked: Boolean,
        loggedIn: Boolean,
        settingsProvider: () -> AuthSettings = { AuthSettingsManager.settings.value }
    ) {
        val prevLocked = lastLockedState
        lastLockedState = locked
        lastLoggedInState = loggedIn

        if (locked && prevLocked != true) {
            onPcLocked(context, loggedIn, settingsProvider())
        } else if (!locked && prevLocked == true) {
            onPcUnlocked(context)
        }
    }

    private fun onPcLocked(context: Context, loggedIn: Boolean, settings: AuthSettings) {
        if (settings.isSnoozed) {
            Log.d(TAG, "PC locked but auth is snoozed; skipping prompt/notification")
            return
        }
        val mode = if (loggedIn) settings.unlockMode else settings.loginMode
        Log.i(TAG, "PC locked (loggedIn=$loggedIn). Triggering mode: $mode")
        when (mode) {
            AuthMode.DENY -> { /* no-op */ }
            AuthMode.NOTIFY -> {
                onNotifyAction?.invoke(context, !loggedIn) ?: AppBridgeNotificationHelper.showPcLockedNotification(
                    context, isLogin = !loggedIn, fullScreen = false
                )
            }
            AuthMode.PROMPT -> {
                onPromptAction?.invoke(context, !loggedIn) ?: run {
                    BiometricAuthBridge.wakeScreen(context)
                    AppBridgeNotificationHelper.showPcLockedNotification(context, isLogin = !loggedIn, fullScreen = true)
                    launchPromptActivity(context, loggedIn)
                }
            }
        }
    }

    private fun onPcUnlocked(context: Context) {
        Log.i(TAG, "PC unlocked; dismissing active prompt and notification")
        onDismissAction?.invoke(context) ?: run {
            AppBridgeNotificationHelper.dismissPcLockedNotification(context)
            BiometricAuthActivity.dismissActivePrompt()
        }
    }

    private fun launchPromptActivity(context: Context, loggedIn: Boolean) {
        try {
            val intent = Intent(context, BiometricAuthActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                if (!loggedIn) {
                    putExtra(BiometricAuthActivity.EXTRA_TITLE, "Singularity Greeter")
                    putExtra(BiometricAuthActivity.EXTRA_REASON, "Log In to PC Session")
                    putExtra(BiometricAuthActivity.EXTRA_IS_LOGIN, true)
                } else {
                    putExtra(BiometricAuthActivity.EXTRA_TITLE, "Singularity Lockscreen")
                    putExtra(BiometricAuthActivity.EXTRA_REASON, "Unlock PC Screen")
                    putExtra(BiometricAuthActivity.EXTRA_IS_UNLOCK, true)
                }
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to launch BiometricAuthActivity: ${e.message}")
        }
    }

    fun reset() {
        lastLockedState = null
        lastLoggedInState = null
    }
}

