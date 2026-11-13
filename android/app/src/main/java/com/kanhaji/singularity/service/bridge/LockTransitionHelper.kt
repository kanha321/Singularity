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
        if (!loggedIn) {
            Log.d(TAG, "PC is at login screen (not logged in); login fingerprint auth is disabled")
            return
        }
        if (settings.isSnoozed) {
            Log.d(TAG, "PC locked but auth is snoozed; skipping prompt/notification")
            return
        }
        val mode = settings.unlockMode
        Log.i(TAG, "PC locked (loggedIn=$loggedIn). Triggering mode: $mode")
        when (mode) {
            AuthMode.DENY -> { /* no-op */ }
            AuthMode.NOTIFY -> {
                onNotifyAction?.invoke(context, false) ?: AppBridgeNotificationHelper.showPcLockedNotification(
                    context, fullScreen = false
                )
            }
            AuthMode.PROMPT -> {
                onPromptAction?.invoke(context, false) ?: run {
                    BiometricAuthBridge.wakeScreen(context)
                    AppBridgeNotificationHelper.showPcLockedNotification(context, fullScreen = true)
                    launchPromptActivity(context)
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

    private fun launchPromptActivity(context: Context) {
        try {
            val intent = Intent(context, BiometricAuthActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(BiometricAuthActivity.EXTRA_TITLE, "Singularity Lockscreen")
                putExtra(BiometricAuthActivity.EXTRA_REASON, "Unlock PC Screen")
                putExtra(BiometricAuthActivity.EXTRA_IS_UNLOCK, true)
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

