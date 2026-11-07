package com.kanhaji.singularity.service

import android.app.KeyguardManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import com.kanhaji.singularity.biometric.BiometricAuthActivity
import com.kanhaji.singularity.service.bridge.*
import kotlinx.coroutines.*

class AppBridgeService : Service() {
    companion object {
        const val TAG = "SingularityAppBridge"
        const val ACTION_RELAUNCH_PROMPT = "com.kanhaji.singularity.action.RELAUNCH_PROMPT"
        const val ACTION_REJECT_AUTH = "com.kanhaji.singularity.action.REJECT_AUTH"

        val currentAuthRequest = AppBridgeState.currentAuthRequest; val pcLockState = AppBridgeState.pcLockState
        val pcLoginState = AppBridgeState.pcLoginState; val isPcConnected = AppBridgeState.isPcConnected
        val isPhoneLocked = AppBridgeState.isPhoneLocked; val isCameraStreaming = AppBridgeState.isCameraStreaming
        val isCameraMicActive = AppBridgeState.isCameraMicActive; val selectedCameraLens = AppBridgeState.selectedCameraLens
        val pendingCameraAction = AppBridgeState.pendingCameraAction; val pendingCameraLens = AppBridgeState.pendingCameraLens
        val pendingMediaAction = AppBridgeState.pendingMediaAction; val currentMediaState = AppBridgeState.currentMediaState
        val fileServerUrl = AppBridgeState.fileServerUrl; val activeConnections = AppBridgeState.activeConnections
        val isActivityVisible = AppBridgeState.isActivityVisible

        fun onActivityStopped(context: Context) { Log.d(TAG, "MainActivity stopped.") }
        fun onPcDisconnected(context: Context? = null) {
            AppBridgeState.resetPcDisconnected(context)
            BiometricAuthBridge.rejectActiveAuth("PC disconnected")
            BiometricAuthActivity.dismissActivePrompt()
            LockTransitionHelper.reset()
            if (context != null) AppBridgeNotificationHelper.dismissPcLockedNotification(context)
        }
        fun requestPcUnlock() = AppBridgeState.requestPcUnlock(); fun requestPcLock() = AppBridgeState.requestPcLock()
        fun requestPcLogout() = AppBridgeState.requestPcLogout(); fun requestPcLogin() = AppBridgeState.requestPcLogin()
        fun requestPowerAction(action: String) = AppBridgeState.requestPowerAction(action)
        fun requestOpenUrlOnDesktop(url: String) = AppBridgeState.requestOpenUrlOnDesktop(url)
        fun requestStartCamera(lens: String = "main") = AppBridgeState.requestStartCamera(lens)
        fun requestStopCamera() = AppBridgeState.requestStopCamera(); fun requestSwitchLens(lens: String) = AppBridgeState.requestSwitchLens(lens)
        fun pushAuthSettings(s: com.kanhaji.singularity.feature.auth.model.AuthSettings) { Log.i(TAG, "Settings: ${s.toJson()}") }
        fun dismissPcLockedNotification(ctx: Context) = AppBridgeNotificationHelper.dismissPcLockedNotification(ctx)
        fun showPcLockedNotification(ctx: Context, isLogin: Boolean = false, fullScreen: Boolean = false) =
            AppBridgeNotificationHelper.showPcLockedNotification(ctx, isLogin, fullScreen)
        fun reportBiometricProgress(e: String, a: Int, m: String? = null) = BiometricAuthBridge.reportBiometricProgress(e, a, m)
        fun reportBiometricResult(s: Boolean, e: String?, a: Int = 0, l: Boolean = false) = BiometricAuthBridge.reportBiometricResult(s, e, a, l)
        fun onBiometricPromptDismissed(ctx: Context) = BiometricAuthBridge.onBiometricPromptDismissed(ctx); fun rejectActiveAuth(reason: String = "User rejected in app") = BiometricAuthBridge.rejectActiveAuth(reason)
        fun relaunchBiometricPrompt(ctx: Context) = BiometricAuthBridge.relaunchBiometricPrompt(ctx)
        fun start(ctx: Context) {
            val intent = Intent(ctx, AppBridgeService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(intent) else ctx.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val socketServer = AppBridgeSocketServer()
    private val udpBeacon = UdpDiscoveryBeacon()

    private val screenLockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val km = getSystemService(KeyguardManager::class.java)
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> AppBridgeState.isPhoneLocked.value = true
                Intent.ACTION_USER_PRESENT -> AppBridgeState.isPhoneLocked.value = false
                Intent.ACTION_SCREEN_ON -> AppBridgeState.isPhoneLocked.value = km?.isKeyguardLocked == true
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            try { Runtime.getRuntime().exec(arrayOf("su", "-c", "appops set $packageName SYSTEM_ALERT_WINDOW allow")) } catch (_: Exception) {}
        }
        AppBridgeNotificationHelper.createChannels(this)
        val notif = AppBridgeNotificationHelper.buildForegroundNotification(this, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(AppBridgeNotificationHelper.NOTIFICATION_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else startForeground(AppBridgeNotificationHelper.NOTIFICATION_ID, notif)
        AppBridgeState.isPhoneLocked.value = getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenLockReceiver, filter)
        socketServer.start(serviceScope, this) {
            try {
                udpBeacon.stop(); socketServer.close(); onPcDisconnected(this)
                AppBridgeNotificationHelper.dismissPcLockedNotification(this)
                stopForeground(STOP_FOREGROUND_REMOVE)
            } catch (_: Exception) {}
            stopSelf()
        }
        startConnectionWatchdog()
        udpBeacon.start(serviceScope)
        com.kanhaji.singularity.feature.audio.stream.AudioStreamClient.instance.initialize(serviceScope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_RELAUNCH_PROMPT -> { BiometricAuthBridge.wakeScreen(this); BiometricAuthBridge.relaunchBiometricPrompt(this) }
            ACTION_REJECT_AUTH -> { BiometricAuthBridge.rejectActiveAuth("Cancelled by user on phone"); BiometricAuthActivity.dismissActivePrompt() }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        com.kanhaji.singularity.feature.audio.stream.AudioStreamClient.instance.release()
        udpBeacon.stop()
        socketServer.close()
        serviceScope.cancel()
        try { unregisterReceiver(screenLockReceiver) } catch (_: Exception) {}
        onPcDisconnected(this)
        super.onDestroy()
    }

    private fun handlePcDisconnected() {
        onPcDisconnected(this)
        AppBridgeNotificationHelper.dismissPcLockedNotification(this)
        AppBridgeNotificationHelper.updateHighwayNotification(this, false)
        udpBeacon.start(serviceScope)
    }

    private fun startConnectionWatchdog() {
        serviceScope.launch {
            while (isActive) {
                delay(1000)
                val elapsed = System.currentTimeMillis() - AppBridgeState.lastPacketReceivedMs.get()
                if (AppBridgeState.isPcConnected.value && elapsed > 10000L) handlePcDisconnected()
            }
        }
    }
}
