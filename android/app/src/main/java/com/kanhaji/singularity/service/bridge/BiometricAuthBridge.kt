package com.kanhaji.singularity.service.bridge

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import com.kanhaji.singularity.biometric.BiometricAuthActivity
import com.kanhaji.singularity.feature.auth.data.AuthSettingsManager
import com.kanhaji.singularity.feature.auth.model.AuthMode
import com.kanhaji.singularity.service.AppBridgeService
import com.kanhaji.singularity.service.AuthResultData
import com.kanhaji.singularity.service.PendingAuth
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

object BiometricAuthBridge {
    private const val TAG = "SingularityAuthBridge"
    private var activePendingAuth: CompletableDeferred<AuthResultData>? = null
    private var activeProgressCallback: ((String, Int, String?) -> Unit)? = null

    fun reportBiometricProgress(event: String, attempt: Int, message: String? = null) {
        try { activeProgressCallback?.invoke(event, attempt, message) } catch (_: Exception) {}
    }

    fun reportBiometricResult(success: Boolean, error: String?, failedAttempts: Int = 0, isLockout: Boolean = false) {
        activePendingAuth?.complete(AuthResultData(success, error, failedAttempts, isLockout))
        AppBridgeState.currentAuthRequest.value = null
    }

    fun rejectActiveAuth(reason: String = "User rejected in app") {
        activePendingAuth?.complete(AuthResultData(false, reason, 0, false))
        AppBridgeState.currentAuthRequest.value = null
    }

    fun onBiometricPromptDismissed(context: Context) {
        val auth = AppBridgeState.currentAuthRequest.value ?: return
        val relaunchIntent = Intent(context, BiometricAuthActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(BiometricAuthActivity.EXTRA_TITLE, "Singularity Security")
            putExtra(BiometricAuthActivity.EXTRA_REASON, auth.reason)
        }
        val relaunchPending = PendingIntent.getActivity(context, 102, relaunchIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val rejectIntent = Intent(context, AppBridgeService::class.java).apply { action = AppBridgeService.ACTION_REJECT_AUTH }
        val rejectPending = PendingIntent.getService(context, 2, rejectIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        AppBridgeNotificationHelper.showAuthRequestNotification(context, auth.reason, auth.timeoutSecs, AuthMode.NOTIFY, relaunchPending, rejectPending)
    }

    fun relaunchBiometricPrompt(context: Context) {
        val auth = AppBridgeState.currentAuthRequest.value ?: return
        val intent = Intent(context, BiometricAuthActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(BiometricAuthActivity.EXTRA_TITLE, "Singularity Security")
            putExtra(BiometricAuthActivity.EXTRA_REASON, auth.reason)
        }
        context.startActivity(intent)
    }

    suspend fun processBiometricAuth(
        context: Context,
        authAction: String,
        reason: String,
        timeoutSecs: Long,
        onProgress: (String, Int, String?) -> Unit
    ): JSONObject {
        val settings = AuthSettingsManager.settings.value
        if (settings.isSnoozed) {
            AuthSettingsManager.addLog(authAction, "Snooze", "Snoozed")
            return JSONObject().put("status", "rejected").put("reason", "snoozed")
        }
        val mode = settings.getModeForAction(authAction)
        if (mode == AuthMode.DENY) {
            AuthSettingsManager.addLog(authAction, "Deny", "Denied")
            return JSONObject().put("status", "rejected").put("reason", "denied_by_policy")
        }
        val effectiveTimeout = if (mode == AuthMode.NOTIFY) settings.notifyTimeoutSecs.toLong() else timeoutSecs.coerceAtLeast(settings.promptTimeoutSecs.toLong())

        val deferred = CompletableDeferred<AuthResultData>()
        activePendingAuth = deferred
        activeProgressCallback = onProgress
        AppBridgeState.currentAuthRequest.value = PendingAuth(reason, effectiveTimeout)

        val intent = Intent(context, BiometricAuthActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(BiometricAuthActivity.EXTRA_TITLE, "Singularity Security")
            putExtra(BiometricAuthActivity.EXTRA_REASON, reason)
        }
        val authPending = PendingIntent.getActivity(context, 101, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val rejectIntent = Intent(context, AppBridgeService::class.java).apply { action = AppBridgeService.ACTION_REJECT_AUTH }
        val rejectPending = PendingIntent.getService(context, 2, rejectIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        try {
            AppBridgeNotificationHelper.cancelAuthNotification(context)
            AppBridgeNotificationHelper.showAuthRequestNotification(context, reason, effectiveTimeout, mode, authPending, rejectPending)
            if (mode == AuthMode.PROMPT) {
                wakeScreen(context)
                try { context.startActivity(intent) } catch (e: Exception) { Log.w(TAG, "Direct launch: ${e.message}") }
            }
            val result = withTimeout(effectiveTimeout * 1000L) { deferred.await() }
            val logResult = if (result.success) "Approved" else "Denied"
            AuthSettingsManager.addLog(authAction, mode.label, logResult)
            return if (result.success) JSONObject().put("status", "success").put("failed_attempts", result.failedAttempts)
            else JSONObject().put("status", "rejected").put("reason", result.error ?: "Cancelled")
        } catch (_: TimeoutCancellationException) {
            AuthSettingsManager.addLog(authAction, mode.label, "Timed Out")
            return JSONObject().put("status", "timeout").put("reason", "Prompt timed out")
        } finally {
            activeProgressCallback = null
            AppBridgeNotificationHelper.cancelAuthNotification(context)
            AppBridgeState.currentAuthRequest.value = null
            activePendingAuth = null
        }
    }

    fun wakeScreen(context: Context) {
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            @Suppress("DEPRECATION")
            val wakeLock = pm?.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP, "singularity:biometric_wake")
            wakeLock?.acquire(30000L)
        } catch (_: Exception) {}
    }
}
