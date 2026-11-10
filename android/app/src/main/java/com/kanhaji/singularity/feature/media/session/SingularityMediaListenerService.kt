package com.kanhaji.singularity.feature.media.session

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.util.Log
import com.kanhaji.singularity.service.bridge.AppBridgeState
import kotlinx.coroutines.*

class SingularityMediaListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "SingularityMediaListener"
        val tracker = PhoneMediaSessionTracker()
        @Volatile private var instance: SingularityMediaListenerService? = null
        val isServiceRunning: Boolean get() = instance != null
        fun getInstance(): SingularityMediaListenerService? = instance
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var mediaSessionManager: MediaSessionManager? = null
    private val handler by lazy { Handler(Looper.getMainLooper()) }
    private val controllerCallbacks = mutableMapOf<MediaController, MediaController.Callback>()

    private val sessionListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        handleSessionsChanged(controllers)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        Log.i(TAG, "Notification & Media Session listener connected")
        val mgr = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
        mediaSessionManager = mgr
        if (mgr != null) {
            val compName = ComponentName(this, SingularityMediaListenerService::class.java)
            try {
                mgr.addOnActiveSessionsChangedListener(sessionListener, compName, handler)
                handleSessionsChanged(mgr.getActiveSessions(compName))
            } catch (e: SecurityException) {
                Log.w(TAG, "SecurityException on active sessions: ${e.message}")
            }
        }
        serviceScope.launch {
            tracker.currentMedia.collect { state ->
                AppBridgeState.currentPhoneMediaState.value = state
            }
        }
        seedActiveNotifications()
    }

    fun seedActiveNotifications() {
        try {
            val active = activeNotifications ?: return
            for (sbn in active) {
                com.kanhaji.singularity.feature.notification.PhoneNotificationTracker.onNotificationPosted(this, sbn)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed seeding active notifications: ${e.message}")
        }
    }

    override fun onNotificationPosted(sbn: android.service.notification.StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn != null) {
            com.kanhaji.singularity.feature.notification.PhoneNotificationTracker.onNotificationPosted(this, sbn)
        }
    }

    override fun onNotificationRemoved(sbn: android.service.notification.StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        if (sbn != null) {
            com.kanhaji.singularity.feature.notification.PhoneNotificationTracker.onNotificationRemoved(sbn)
        }
    }

    override fun onListenerDisconnected() {
        instance = null
        cleanupCallbacks()
        try { mediaSessionManager?.removeOnActiveSessionsChangedListener(sessionListener) } catch (_: Exception) {}
        mediaSessionManager = null
        tracker.clear()
        AppBridgeState.currentPhoneMediaState.value = null
        serviceScope.cancel()
        super.onListenerDisconnected()
    }

    private fun handleSessionsChanged(controllers: List<MediaController>?) {
        cleanupCallbacks()
        val list = controllers ?: emptyList()
        val bridges = list.map { ctrl ->
            val callback = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: android.media.session.PlaybackState?) {
                    tracker.refreshCurrentMedia()
                }
                override fun onMetadataChanged(metadata: android.media.MediaMetadata?) {
                    tracker.refreshCurrentMedia()
                }
                override fun onSessionDestroyed() {
                    tracker.refreshCurrentMedia()
                }
            }
            try { ctrl.registerCallback(callback, handler) } catch (_: Exception) {}
            controllerCallbacks[ctrl] = callback
            AndroidMediaSessionBridge(ctrl)
        }
        tracker.updateSessions(bridges)
    }

    private fun cleanupCallbacks() {
        for ((ctrl, cb) in controllerCallbacks) {
            try { ctrl.unregisterCallback(cb) } catch (_: Exception) {}
        }
        controllerCallbacks.clear()
    }
}

