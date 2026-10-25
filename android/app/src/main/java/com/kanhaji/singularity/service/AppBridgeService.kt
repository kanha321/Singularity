package com.kanhaji.singularity.service

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.LocalServerSocket
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.kanhaji.singularity.MainActivity
import com.kanhaji.singularity.R
import com.kanhaji.singularity.biometric.BiometricAuthActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.PrintWriter
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

data class PendingAuth(
    val reason: String,
    val timeoutSecs: Long,
    val startTime: Long = System.currentTimeMillis()
)

data class AuthResultData(
    val success: Boolean,
    val error: String?,
    val failedAttempts: Int = 0,
    val isLockout: Boolean = false
)

class AppBridgeService : Service() {

    companion object {
        const val TAG = "SingularityAppBridge"
        const val CHANNEL_ID = "singularity_highway_channel"
        const val AUTH_CHANNEL_ID = "singularity_auth_alerts_v6"
        const val LOCK_STATUS_CHANNEL_ID = "singularity_lock_status_v2"
        const val NOTIFICATION_ID = 2828
        const val AUTH_NOTIFICATION_ID = 2829
        const val PC_LOCKED_NOTIFICATION_ID = 2830
        const val SOCKET_NAME = "singularity_app"
        const val TCP_PORT = 2828
        const val ACTION_RELAUNCH_PROMPT = "com.kanhaji.singularity.action.RELAUNCH_PROMPT"
        const val ACTION_REJECT_AUTH = "com.kanhaji.singularity.action.REJECT_AUTH"

        val currentAuthRequest = MutableStateFlow<PendingAuth?>(null)
        val pcLockState = MutableStateFlow<String>("disconnected")
        val pcLoginState = MutableStateFlow<Boolean>(false)
        val isPcConnected = MutableStateFlow<Boolean>(false)
        val isPhoneLocked = MutableStateFlow<Boolean>(false)
        val pendingUnlockRequest = AtomicBoolean(false)
        val pendingLockRequest = AtomicBoolean(false)
        val pendingLogoutRequest = AtomicBoolean(false)
        val pendingLoginRequest = AtomicBoolean(false)
        val pendingPowerAction = AtomicReference<String?>(null)
        val fileServerUrl = MutableStateFlow<String>("http://127.0.0.1:8920")
        val lastPacketReceivedMs = AtomicLong(0L)
        val activeConnections = AtomicInteger(0)
        val isActivityVisible = AtomicBoolean(false)
        private var activeServiceInstance: AppBridgeService? = null
        private var activePendingAuth: CompletableDeferred<AuthResultData>? = null
        private var activeProgressCallback: ((String, Int, String?) -> Unit)? = null

        fun onActivityStopped(context: Context) {
            Log.d(TAG, "MainActivity stopped. Checking if service should enter disconnect shutdown...")
            activeServiceInstance?.checkDisconnectGracePeriod()
        }

        fun onPcDisconnected(context: Context? = null) {
            if (!isPcConnected.value && pcLockState.value == "disconnected") return
            Log.i(TAG, "Transitioning to DISCONNECTED state: resetting all PC values")
            isPcConnected.value = false
            pcLockState.value = "disconnected"
            pcLoginState.value = false
            currentAuthRequest.value = null
            pendingUnlockRequest.set(false)
            pendingLockRequest.set(false)
            pendingLogoutRequest.set(false)
            pendingLoginRequest.set(false)
            pendingPowerAction.set(null)
            rejectActiveAuth("PC disconnected")
            BiometricAuthActivity.dismissActivePrompt()
            if (context != null) {
                dismissPcLockedNotification(context)
            }
        }

        fun requestPcUnlock() {
            pendingUnlockRequest.set(true)
            Log.i(TAG, "PC unlock requested via biometric authentication")
        }

        fun requestPcLock() {
            pendingLockRequest.set(true)
            Log.i(TAG, "PC lock requested by user")
        }

        fun requestPcLogout() {
            pendingLogoutRequest.set(true)
            Log.i(TAG, "PC logout requested by user")
        }

        fun requestPcLogin() {
            pendingLoginRequest.set(true)
            pendingUnlockRequest.set(true)
            Log.i(TAG, "PC login requested via biometric authentication")
        }

        fun requestPowerAction(action: String) {
            pendingPowerAction.set(action)
            Log.i(TAG, "PC power action requested: $action")
        }

        fun pushAuthSettings(settings: com.kanhaji.singularity.feature.auth.model.AuthSettings) {
            Log.i(TAG, "Auth settings updated: ${settings.toJson()}")
        }

        fun dismissPcLockedNotification(context: Context) {
            try {
                val notifManager = context.getSystemService(NotificationManager::class.java)
                notifManager?.cancel(PC_LOCKED_NOTIFICATION_ID)
            } catch (_: Exception) {}
        }

        fun showPcLockedNotification(context: Context) {
            try {
                val notifManager = context.getSystemService(NotificationManager::class.java) ?: return
                val intent = Intent(context, BiometricAuthActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_NO_ANIMATION
                    putExtra(BiometricAuthActivity.EXTRA_TITLE, "Singularity Lockscreen")
                    putExtra(BiometricAuthActivity.EXTRA_REASON, "Unlock PC Screen")
                    putExtra(BiometricAuthActivity.EXTRA_IS_UNLOCK, true)
                }
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    101,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                notifManager.cancel(PC_LOCKED_NOTIFICATION_ID)

                val builder = NotificationCompat.Builder(context, AUTH_CHANNEL_ID)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle("💻 PC Screen Locked")
                    .setContentText("Tap to unlock laptop with fingerprint")
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_EVENT)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setDefaults(NotificationCompat.DEFAULT_ALL)
                    .setVibrate(longArrayOf(0, 200, 100, 200))
                    .setAutoCancel(true)
                    .setTimeoutAfter(120_000L)
                    .setContentIntent(pendingIntent)
                    .addAction(R.mipmap.ic_launcher, "🔓 Unlock with Fingerprint", pendingIntent)

                notifManager.notify(PC_LOCKED_NOTIFICATION_ID, builder.build())
            } catch (e: Exception) {
                Log.w(TAG, "Error posting PC locked notification: ${e.message}")
            }
        }

        fun reportBiometricProgress(event: String, attempt: Int, message: String? = null) {
            try {
                activeProgressCallback?.invoke(event, attempt, message)
            } catch (_: Exception) {}
        }

        fun reportBiometricResult(success: Boolean, error: String?, failedAttempts: Int = 0, isLockout: Boolean = false) {
            activePendingAuth?.complete(AuthResultData(success, error, failedAttempts, isLockout))
            currentAuthRequest.value = null
        }

        fun onBiometricPromptDismissed(context: Context) {
            Log.i(TAG, "Biometric prompt dismissed, keeping notification active for relaunch")
            val auth = currentAuthRequest.value ?: return
            val notifManager = context.getSystemService(NotificationManager::class.java) ?: return

            val relaunchIntent = Intent(context, AppBridgeService::class.java).apply {
                action = ACTION_RELAUNCH_PROMPT
            }
            val relaunchPending = PendingIntent.getService(
                context,
                1,
                relaunchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val rejectIntent = Intent(context, AppBridgeService::class.java).apply {
                action = ACTION_REJECT_AUTH
            }
            val rejectPending = PendingIntent.getService(
                context,
                2,
                rejectIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notif = NotificationCompat.Builder(context, AUTH_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Singularity Authentication Request")
                .setContentText("Tap to touch fingerprint on POCO F5")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_EVENT)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(relaunchPending)
                .setAutoCancel(true)
                .addAction(R.mipmap.ic_launcher, "Verify Fingerprint", relaunchPending)
                .addAction(R.mipmap.ic_launcher, "Use Password", rejectPending)
                .build()

            notifManager.notify(AUTH_NOTIFICATION_ID, notif)
        }

        fun rejectActiveAuth(reason: String = "User rejected in app") {
            activePendingAuth?.complete(AuthResultData(false, reason, 0, false))
            currentAuthRequest.value = null
        }

        fun relaunchBiometricPrompt(context: Context) {
            val auth = currentAuthRequest.value ?: return
            val intent = Intent(context, BiometricAuthActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION
                putExtra(BiometricAuthActivity.EXTRA_TITLE, "Singularity Security")
                putExtra(BiometricAuthActivity.EXTRA_REASON, auth.reason)
            }
            context.startActivity(intent)
        }

        fun start(context: Context) {
            val intent = Intent(context, AppBridgeService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var localServerSocket: LocalServerSocket? = null
    private var tcpServerSocket: ServerSocket? = null
    private var shutdownTimerJob: Job? = null

    private val screenLockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val km = getSystemService(KeyguardManager::class.java)
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> isPhoneLocked.value = true
                Intent.ACTION_USER_PRESENT -> isPhoneLocked.value = false
                Intent.ACTION_SCREEN_ON -> isPhoneLocked.value = km?.isKeyguardLocked == true
            }
            Log.d(TAG, "Screen lock event: ${intent?.action}, isPhoneLocked=${isPhoneLocked.value}")
        }
    }

    override fun onCreate() {
        super.onCreate()
        activeServiceInstance = this
        shutdownTimerJob?.cancel()
        shutdownTimerJob = null
        createNotificationChannel()
        startForegroundServiceNotification()

        val km = getSystemService(KeyguardManager::class.java)
        isPhoneLocked.value = km?.isKeyguardLocked == true

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenLockReceiver, filter)

        startSocketServer()
        startConnectionWatchdog()
        checkDisconnectGracePeriod()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_RELAUNCH_PROMPT -> {
                Log.i(TAG, "ACTION_RELAUNCH_PROMPT received: waking screen and relaunching prompt")
                wakeScreen()
                relaunchBiometricPrompt(this)
            }
            ACTION_REJECT_AUTH -> {
                Log.i(TAG, "ACTION_REJECT_AUTH received: rejecting auth per user request")
                rejectActiveAuth("Cancelled by user on phone")
                val notifManager = getSystemService(NotificationManager::class.java)
                notifManager?.cancel(AUTH_NOTIFICATION_ID)
                BiometricAuthActivity.dismissActivePrompt()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        shutdownTimerJob?.cancel()
        shutdownTimerJob = null
        if (activeServiceInstance == this) activeServiceInstance = null
        serviceScope.cancel()
        try { unregisterReceiver(screenLockReceiver) } catch (_: Exception) {}
        try { localServerSocket?.close() } catch (_: Exception) {}
        try { tcpServerSocket?.close() } catch (_: Exception) {}
        onPcDisconnected(this)
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val highwayChannel = NotificationChannel(
                CHANNEL_ID,
                "Singularity Highway",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Maintains persistent highway connection with Arch Linux"
            }
            val authChannel = NotificationChannel(
                AUTH_CHANNEL_ID,
                "Singularity Authentication",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Real-time biometric authorization prompts for Linux sudo and screen unlock"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setBypassDnd(true)
                enableVibration(true)
                enableLights(true)
                vibrationPattern = longArrayOf(0, 200, 100, 200)
                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT)
                    .build()
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), audioAttributes)
            }
            val lockStatusChannel = NotificationChannel(
                LOCK_STATUS_CHANNEL_ID,
                "Singularity PC Lock Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Silent status indicator when PC screen is locked"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                enableVibration(false)
                setSound(null, null)
            }
            val manager = getSystemService(NotificationManager::class.java)
            try {
                manager.deleteNotificationChannel("singularity_lock_status_channel")
                manager.deleteNotificationChannel("singularity_auth_channel")
                manager.deleteNotificationChannel("singularity_auth_alerts_v2")
                manager.deleteNotificationChannel("singularity_auth_alerts_v3")
            } catch (_: Exception) {}
            manager.createNotificationChannel(highwayChannel)
            manager.createNotificationChannel(authChannel)
            manager.createNotificationChannel(lockStatusChannel)
        }
    }

    private fun getMainActivityPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            this,
            100,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun startForegroundServiceNotification() {
        val pendingIntent = getMainActivityPendingIntent()
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Singularity Convergence")
            .setContentText("AppBridge active & listening on localabstract:singularity_app")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateHighwayNotification(connected: Boolean) {
        try {
            val text = if (connected) {
                "🟢 Connected to Arch Linux (Desktop)"
            } else {
                "⚪ Highway listening on localabstract:singularity_app"
            }
            val pendingIntent = getMainActivityPendingIntent()
            val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Singularity Convergence")
                .setContentText(text)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(pendingIntent)
                .build()
            val manager = getSystemService(NotificationManager::class.java)
            manager?.notify(NOTIFICATION_ID, notification)
        } catch (_: Exception) {}
    }

    fun checkDisconnectGracePeriod() {
        if (!isPcConnected.value && !isActivityVisible.get()) {
            if (shutdownTimerJob == null || shutdownTimerJob?.isActive != true) {
                shutdownTimerJob = serviceScope.launch {
                    Log.i(TAG, "PC disconnected and app in background. Shutdown grace period started (15s)...")
                    delay(15_000L)
                    if (!isPcConnected.value && !isActivityVisible.get()) {
                        Log.i(TAG, "Shutdown grace period expired. Gracefully terminating AppBridgeService.")
                        teardownAndStopService()
                    } else {
                        Log.i(TAG, "Shutdown cancelled: state resumed (connected=${isPcConnected.value}, activityVisible=${isActivityVisible.get()})")
                    }
                }
            }
        }
    }

    private fun handlePcDisconnected() {
        onPcDisconnected(this)
        dismissPcLockedNotification()
        updateHighwayNotification(false)
        checkDisconnectGracePeriod()
    }

    private fun teardownAndStopService() {
        try {
            shutdownTimerJob?.cancel()
            shutdownTimerJob = null
            onPcDisconnected(this)
            dismissPcLockedNotification()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.cancel(NOTIFICATION_ID)
            manager?.cancel(AUTH_NOTIFICATION_ID)
            manager?.cancel(PC_LOCKED_NOTIFICATION_ID)

            try { localServerSocket?.close() } catch (_: Exception) {}
            try { tcpServerSocket?.close() } catch (_: Exception) {}

            stopSelf()
        } catch (e: Exception) {
            Log.w(TAG, "Error during teardownAndStopService: ${e.message}")
            stopSelf()
        }
    }

    private fun startConnectionWatchdog() {
        serviceScope.launch {
            while (isActive) {
                delay(500)
                val elapsed = System.currentTimeMillis() - lastPacketReceivedMs.get()
                if (isPcConnected.value && elapsed > 2000L) {
                    Log.i(TAG, "Watchdog: PC packet timeout (${elapsed}ms), resetting all PC states to disconnected")
                    handlePcDisconnected()
                }
            }
        }
    }

    private fun startSocketServer() {
        serviceScope.launch {
            try {
                val server = LocalServerSocket(SOCKET_NAME)
                localServerSocket = server
                Log.i(TAG, "AppBridge LocalServerSocket listening: localabstract:$SOCKET_NAME")

                while (isActive) {
                    val client = server.accept()
                    launch { handleStream(client.inputStream, client.outputStream) { client.close() } }
                }
            } catch (e: Exception) {
                if (isActive) {
                    Log.e(TAG, "LocalServerSocket error", e)
                }
            }
        }

        serviceScope.launch {
            try {
                val server = ServerSocket(TCP_PORT)
                tcpServerSocket = server
                Log.i(TAG, "AppBridge TCP server listening on port $TCP_PORT")

                while (isActive) {
                    val client = server.accept()
                    launch { handleStream(client.getInputStream(), client.getOutputStream()) { client.close() } }
                }
            } catch (e: Exception) {
                Log.w(TAG, "TCP listener unavailable: ${e.message}")
            }
        }
    }

    private suspend fun handleStream(
        inputStream: InputStream,
        outputStream: OutputStream,
        onClose: () -> Unit
    ) = withContext(Dispatchers.IO) {
        activeConnections.incrementAndGet()
        try {
            val reader = BufferedReader(InputStreamReader(inputStream))
            val writer = PrintWriter(outputStream, true)

            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val reqStr = line?.trim() ?: continue
                if (reqStr.isEmpty()) continue

                lastPacketReceivedMs.set(System.currentTimeMillis())
                if (shutdownTimerJob?.isActive == true) {
                    Log.i(TAG, "Packet received from PC during grace period; cancelling shutdown timer")
                    shutdownTimerJob?.cancel()
                    shutdownTimerJob = null
                }
                if (!isPcConnected.value) {
                    isPcConnected.value = true
                    updateHighwayNotification(true)
                }

                val json = try {
                    JSONObject(reqStr)
                } catch (e: Exception) {
                    writer.println(JSONObject().put("status", "error").put("message", "invalid json").toString())
                    continue
                }

                val action = json.optString("action")
                val response = when (action) {
                    "ping" -> JSONObject().put("status", "pong")
                    "status" -> JSONObject()
                        .put("status", "online")
                        .put("package", packageName)
                    "shutdown", "terminate_service" -> {
                        Log.i(TAG, "Explicit termination requested by PC daemon")
                        writer.println(JSONObject().put("status", "shutting_down").toString())
                        writer.flush()
                        serviceScope.launch {
                            delay(100)
                            teardownAndStopService()
                        }
                        JSONObject().put("status", "shutting_down")
                    }
                    "authenticate" -> {
                        val reason = json.optString("reason", "Host Authentication")
                        val timeout = json.optLong("timeout_secs", 15L)
                        val authAction = json.optString("auth_action", json.optString("action_type", "sudo"))
                        processBiometricAuth(authAction, reason, timeout) { event, attempt, message ->
                            try {
                                val progressJson = JSONObject().put("event", event).put("attempt", attempt)
                                if (message != null) progressJson.put("message", message)
                                writer.println(progressJson.toString())
                                writer.flush()
                            } catch (e: Exception) {
                                Log.w(TAG, "Error writing progress: ${e.message}")
                            }
                        }
                    }
                    "cancel_auth" -> {
                        rejectActiveAuth("Cancelled by user on host")
                        BiometricAuthActivity.dismissActivePrompt()
                        val notifManager = getSystemService(NotificationManager::class.java)
                        notifManager?.cancel(AUTH_NOTIFICATION_ID)
                        JSONObject().put("status", "ok")
                    }
                    "lock_state" -> {
                        val locked = json.optBoolean("locked", false)
                        val loggedIn = json.optBoolean("logged_in", json.optBoolean("isLoggedIn", true))
                        val stateStr = if (locked) "locked" else "unlocked"
                        val stateChanged = pcLockState.value != stateStr
                        pcLockState.value = stateStr
                        pcLoginState.value = loggedIn
                        val host = json.optString("file_server_host", "")
                        val port = json.optInt("file_server_port", 8920)
                        if (host.isNotEmpty()) {
                            fileServerUrl.value = "http://$host:$port"
                        }
                        if (stateChanged) {
                            if (locked) {
                                showPcLockedPrompt()
                            } else {
                                dismissPcLockedNotification()
                                BiometricAuthActivity.dismissActivePrompt()
                            }
                        }
                        val reqUnlock = pendingUnlockRequest.getAndSet(false)
                        val reqLock = pendingLockRequest.getAndSet(false)
                        val reqLogout = pendingLogoutRequest.getAndSet(false)
                        val reqLogin = pendingLoginRequest.getAndSet(false)
                        val reqPower = pendingPowerAction.getAndSet(null)
                        val resp = JSONObject()
                            .put("status", "ok")
                            .put("pc_locked", locked)
                            .put("logged_in", loggedIn)
                            .put("isLoggedIn", loggedIn)
                            .put("request_unlock", reqUnlock || reqLogin)
                            .put("request_lock", reqLock)
                            .put("request_logout", reqLogout)
                            .put("auth_settings", com.kanhaji.singularity.feature.auth.data.AuthSettingsManager.settings.value.toJson())
                        if (reqPower != null) {
                            resp.put("request_power", reqPower)
                        }
                        resp
                    }
                    else -> JSONObject().put("status", "unsupported")
                }

                writer.println(response.toString())
            }
        } catch (e: Exception) {
            Log.w(TAG, "Client connection ended: ${e.message}")
        } finally {
            val remaining = activeConnections.decrementAndGet()
            try { onClose() } catch (_: Exception) {}
            if (remaining <= 0) {
                serviceScope.launch {
                    delay(350)
                    if (activeConnections.get() <= 0 && System.currentTimeMillis() - lastPacketReceivedMs.get() > 1000L) {
                        if (isPcConnected.value) {
                            Log.i(TAG, "All streams closed and timeout elapsed, resetting all PC states to disconnected")
                            handlePcDisconnected()
                        }
                    }
                }
            }
        }
    }

    private suspend fun processBiometricAuth(
        authAction: String,
        reason: String,
        timeoutSecs: Long,
        onProgress: (String, Int, String?) -> Unit
    ): JSONObject {
        val settings = com.kanhaji.singularity.feature.auth.data.AuthSettingsManager.settings.value
        if (settings.isSnoozed) {
            com.kanhaji.singularity.feature.auth.data.AuthSettingsManager.addLog(authAction, "Snooze", "Snoozed")
            return JSONObject().put("status", "rejected").put("reason", "snoozed")
        }

        val mode = settings.getModeForAction(authAction)
        if (mode == com.kanhaji.singularity.feature.auth.model.AuthMode.DENY) {
            com.kanhaji.singularity.feature.auth.data.AuthSettingsManager.addLog(authAction, "Deny", "Denied")
            return JSONObject().put("status", "rejected").put("reason", "denied_by_policy")
        }

        val effectiveTimeout = if (mode == com.kanhaji.singularity.feature.auth.model.AuthMode.NOTIFY) {
            settings.notifyTimeoutSecs.toLong()
        } else {
            timeoutSecs.coerceAtLeast(settings.promptTimeoutSecs.toLong())
        }

        val deferred = CompletableDeferred<AuthResultData>()
        activePendingAuth = deferred
        activeProgressCallback = onProgress
        currentAuthRequest.value = PendingAuth(reason, effectiveTimeout)

        val intent = Intent(this, BiometricAuthActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION
            putExtra(BiometricAuthActivity.EXTRA_TITLE, "Singularity Security")
            putExtra(BiometricAuthActivity.EXTRA_REASON, reason)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val relaunchIntent = Intent(this, AppBridgeService::class.java).apply {
            action = ACTION_RELAUNCH_PROMPT
        }
        val relaunchPending = PendingIntent.getService(
            this,
            1,
            relaunchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val rejectIntent = Intent(this, AppBridgeService::class.java).apply {
            action = ACTION_REJECT_AUTH
        }
        val rejectPending = PendingIntent.getService(
            this,
            2,
            rejectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notifManager = getSystemService(NotificationManager::class.java)

        try {
            notifManager?.cancel(AUTH_NOTIFICATION_ID)

            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            val isInteractive = pm?.isInteractive ?: true

            val builder = NotificationCompat.Builder(this, AUTH_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Singularity Authentication Request")
                .setContentText(reason)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_EVENT)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setVibrate(longArrayOf(0, 200, 100, 200))
                .setContentIntent(relaunchPending)
                .setAutoCancel(true)
                .setTimeoutAfter(effectiveTimeout * 1000L)
                .addAction(R.mipmap.ic_launcher, "Verify with Fingerprint", relaunchPending)
                .addAction(R.mipmap.ic_launcher, "Use Password", rejectPending)

            if (mode == com.kanhaji.singularity.feature.auth.model.AuthMode.PROMPT && !isInteractive) {
                builder.setFullScreenIntent(pendingIntent, true)
            }

            notifManager.notify(AUTH_NOTIFICATION_ID, builder.build())

            if (mode == com.kanhaji.singularity.feature.auth.model.AuthMode.PROMPT) {
                wakeScreen()
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.w(TAG, "Direct startActivity blocked by BAL: ${e.message}")
                }
            }

            val result = withTimeout(effectiveTimeout * 1000L) {
                deferred.await()
            }
            val logResult = if (result.success) {
                if (result.failedAttempts > 0) "Approved (${result.failedAttempts} retries)" else "Approved"
            } else {
                if (result.isLockout) "Locked Out (${result.failedAttempts} attempts)" else "Denied (${result.failedAttempts} attempts)"
            }
            com.kanhaji.singularity.feature.auth.data.AuthSettingsManager.addLog(
                authAction,
                mode.label,
                logResult
            )
            return if (result.success) {
                JSONObject().put("status", "success").put("failed_attempts", result.failedAttempts)
            } else {
                JSONObject().put("status", "rejected").put("reason", result.error ?: "Cancelled")
                    .put("failed_attempts", result.failedAttempts)
                    .put("is_lockout", result.isLockout)
            }
        } catch (e: TimeoutCancellationException) {
            com.kanhaji.singularity.feature.auth.data.AuthSettingsManager.addLog(
                authAction,
                mode.label,
                "Timed Out"
            )
            return JSONObject().put("status", "timeout").put("reason", "Prompt timed out")
        } finally {
            activeProgressCallback = null
            notifManager?.cancel(AUTH_NOTIFICATION_ID)
            currentAuthRequest.value = null
            activePendingAuth = null
        }
    }

    private fun wakeScreen() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            @Suppress("DEPRECATION")
            val wakeLock = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "singularity:biometric_wake"
            )
            wakeLock.acquire(30000L)
        } catch (e: Exception) {
            Log.w(TAG, "Failed acquiring wake lock: ${e.message}")
        }
    }

    private fun showPcLockedPrompt() {
        val settings = com.kanhaji.singularity.feature.auth.data.AuthSettingsManager.settings.value
        if (settings.isSnoozed) {
            Log.i(TAG, "PC locked, but snooze active - skipping prompt")
            return
        }

        when (settings.unlockMode) {
            com.kanhaji.singularity.feature.auth.model.AuthMode.DENY -> {
                Log.i(TAG, "PC locked, unlock policy is DENY - skipping prompt and notification")
                dismissPcLockedNotification()
            }
            com.kanhaji.singularity.feature.auth.model.AuthMode.NOTIFY -> {
                Log.i(TAG, "PC locked, unlock policy is NOTIFY - posting notification only")
                showPcLockedNotification(this)
            }
            com.kanhaji.singularity.feature.auth.model.AuthMode.PROMPT -> {
                Log.i(TAG, "PC locked, unlock policy is PROMPT - waking screen and showing prompt")
                showPcLockedNotification(this)
                wakeScreen()
                val intent = Intent(this, BiometricAuthActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_NO_ANIMATION
                    putExtra(BiometricAuthActivity.EXTRA_TITLE, "Singularity Lockscreen")
                    putExtra(BiometricAuthActivity.EXTRA_REASON, "Unlock PC Screen")
                    putExtra(BiometricAuthActivity.EXTRA_IS_UNLOCK, true)
                }
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.w(TAG, "Direct launch error: ${e.message}")
                }
            }
        }
    }

    private fun dismissPcLockedNotification() {
        val notifManager = getSystemService(NotificationManager::class.java)
        notifManager.cancel(PC_LOCKED_NOTIFICATION_ID)
    }
}
