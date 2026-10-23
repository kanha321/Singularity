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

data class PendingAuth(
    val reason: String,
    val timeoutSecs: Long,
    val startTime: Long = System.currentTimeMillis()
)

class AppBridgeService : Service() {

    companion object {
        const val TAG = "SingularityAppBridge"
        const val CHANNEL_ID = "singularity_highway_channel"
        const val AUTH_CHANNEL_ID = "singularity_auth_channel"
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
        val lastPacketReceivedMs = AtomicLong(0L)
        val activeConnections = AtomicInteger(0)
        private var activePendingAuth: CompletableDeferred<Pair<Boolean, String?>>? = null

        fun onPcDisconnected(context: Context? = null) {
            if (!isPcConnected.value && pcLockState.value == "disconnected") return
            Log.i(TAG, "Transitioning to DISCONNECTED state: resetting all PC values")
            isPcConnected.value = false
            pcLockState.value = "disconnected"
            pcLoginState.value = false
            currentAuthRequest.value = null
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

                val notification = NotificationCompat.Builder(context, LOCK_STATUS_CHANNEL_ID)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle("💻 PC Screen Locked")
                    .setContentText("Touch fingerprint sensor to unlock laptop")
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setCategory(NotificationCompat.CATEGORY_STATUS)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setOngoing(true)
                    .setSilent(true)
                    .setOnlyAlertOnce(true)
                    .setAutoCancel(false)
                    .setContentIntent(pendingIntent)
                    .addAction(R.mipmap.ic_launcher, "🔓 Unlock with Fingerprint", pendingIntent)
                    .build()

                notifManager.notify(PC_LOCKED_NOTIFICATION_ID, notification)
            } catch (e: Exception) {
                Log.w(TAG, "Error posting PC locked notification: ${e.message}")
            }
        }

        fun reportBiometricResult(success: Boolean, error: String?) {
            activePendingAuth?.complete(Pair(success, error))
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
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(relaunchPending)
                .setOngoing(true)
                .setAutoCancel(false)
                .addAction(R.mipmap.ic_launcher, "Verify Fingerprint", relaunchPending)
                .addAction(R.mipmap.ic_launcher, "Use Password", rejectPending)
                .build()

            notifManager.notify(AUTH_NOTIFICATION_ID, notif)
        }

        fun rejectActiveAuth(reason: String = "User rejected in app") {
            activePendingAuth?.complete(Pair(false, reason))
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
        serviceScope.cancel()
        try { unregisterReceiver(screenLockReceiver) } catch (_: Exception) {}
        try { localServerSocket?.close() } catch (_: Exception) {}
        try { tcpServerSocket?.close() } catch (_: Exception) {}
        handlePcDisconnected()
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

    private fun handlePcDisconnected() {
        onPcDisconnected(this)
        dismissPcLockedNotification()
        updateHighwayNotification(false)
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
                    "authenticate" -> {
                        val reason = json.optString("reason", "Host Authentication")
                        val timeout = json.optLong("timeout_secs", 15L)
                        processBiometricAuth(reason, timeout)
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
                        if (stateChanged) {
                            if (locked) {
                                showPcLockedPrompt()
                            } else {
                                dismissPcLockedNotification()
                                BiometricAuthActivity.dismissActivePrompt()
                            }
                        }
                        val reqUnlock = pendingUnlockRequest.getAndSet(false)
                        JSONObject()
                            .put("status", "ok")
                            .put("pc_locked", locked)
                            .put("logged_in", loggedIn)
                            .put("isLoggedIn", loggedIn)
                            .put("request_unlock", reqUnlock)
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

    private suspend fun processBiometricAuth(reason: String, timeoutSecs: Long): JSONObject {
        wakeScreen()

        val deferred = CompletableDeferred<Pair<Boolean, String?>>()
        activePendingAuth = deferred
        currentAuthRequest.value = PendingAuth(reason, timeoutSecs)

        val intent = Intent(this, BiometricAuthActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
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
            startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Direct startActivity blocked by BAL; falling back to notification: ${e.message}")
            val authNotification = NotificationCompat.Builder(this, AUTH_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Singularity Authentication Request")
                .setContentText(reason)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOnlyAlertOnce(true)
                .setFullScreenIntent(pendingIntent, true)
                .setContentIntent(relaunchPending)
                .setOngoing(true)
                .setAutoCancel(false)
                .addAction(R.mipmap.ic_launcher, "Verify with Fingerprint", relaunchPending)
                .addAction(R.mipmap.ic_launcher, "Use Password", rejectPending)
                .build()

            notifManager.notify(AUTH_NOTIFICATION_ID, authNotification)
        }

        return try {
            val (success, err) = withTimeout(timeoutSecs * 1000L) {
                deferred.await()
            }
            if (success) {
                JSONObject().put("status", "success")
            } else {
                JSONObject().put("status", "rejected").put("reason", err ?: "Cancelled")
            }
        } catch (e: TimeoutCancellationException) {
            JSONObject().put("status", "timeout").put("reason", "Prompt timed out")
        } finally {
            notifManager.cancel(AUTH_NOTIFICATION_ID)
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
            showPcLockedNotification(this)
        }
    }

    private fun dismissPcLockedNotification() {
        val notifManager = getSystemService(NotificationManager::class.java)
        notifManager.cancel(PC_LOCKED_NOTIFICATION_ID)
    }
}
