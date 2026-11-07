package com.kanhaji.singularity.feature.audio.route

import android.media.MediaRoute2Info
import android.media.MediaRoute2ProviderService
import android.media.RoutingSessionInfo
import android.os.Bundle
import android.util.Log
import com.kanhaji.singularity.feature.audio.model.LaptopAudioSink
import com.kanhaji.singularity.service.bridge.AppBridgeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class SingularityMediaRoute2ProviderService : MediaRoute2ProviderService() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val activeSessions = ConcurrentHashMap<String, String>()

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "SingularityMediaRoute2ProviderService created")
        scope.launch {
            AppBridgeState.availableLaptopAudioSinks.collect { sinks ->
                if (sinks.isEmpty() && activeSessions.isNotEmpty()) {
                    val sessionIds = ArrayList(activeSessions.keys)
                    activeSessions.clear()
                    for (sid in sessionIds) {
                        try {
                            notifySessionReleased(sid)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to release session on disconnect: ${e.message}")
                        }
                    }
                }
                publishRoutes(sinks)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        val sessionIds = ArrayList(activeSessions.keys)
        activeSessions.clear()
        for (sid in sessionIds) {
            try { notifySessionReleased(sid) } catch (_: Exception) {}
        }
        scope.cancel()
        Log.i(TAG, "SingularityMediaRoute2ProviderService destroyed")
    }

    private fun publishRoutes(sinks: List<LaptopAudioSink>) {
        val routes = sinks.map { AudioRouteMapper.buildMediaRoute(it) }
        try {
            notifyRoutes(routes)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to notify routes: ${e.message}")
        }
    }

    override fun onCreateSession(requestId: Long, pkgName: String, routeId: String, hints: Bundle?) {
        val sessionId = UUID.randomUUID().toString()
        activeSessions[sessionId] = routeId
        AppBridgeState.setAudioRoute(routeId)
        try {
            val session = RoutingSessionInfo.Builder(sessionId, pkgName)
                .addSelectedRoute(routeId)
                .setName("Laptop Audio")
                .setVolumeHandling(MediaRoute2Info.PLAYBACK_VOLUME_VARIABLE)
                .build()
            notifySessionCreated(requestId, session)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to notify session created: ${e.message}")
        }
    }

    override fun onReleaseSession(requestId: Long, sessionId: String) {
        activeSessions.remove(sessionId)
        if (activeSessions.isEmpty()) {
            AppBridgeState.setAudioRoute("phone")
        }
        try {
            notifySessionReleased(sessionId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to notify session released: ${e.message}")
        }
    }

    override fun onTransferToRoute(requestId: Long, sessionId: String, routeId: String) {
        activeSessions[sessionId] = routeId
        AppBridgeState.setAudioRoute(routeId)
        try {
            val pkg = getSessionInfo(sessionId)?.clientPackageName ?: packageName
            val session = RoutingSessionInfo.Builder(sessionId, pkg)
                .addSelectedRoute(routeId)
                .setName("Laptop Audio")
                .setVolumeHandling(MediaRoute2Info.PLAYBACK_VOLUME_VARIABLE)
                .build()
            notifySessionUpdated(session)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to notify session updated: ${e.message}")
        }
    }

    override fun onSetRouteVolume(requestId: Long, routeId: String, volume: Int) {
        AppBridgeState.setSinkVolume(routeId, volume)
    }

    override fun onSetSessionVolume(requestId: Long, sessionId: String, volume: Int) {
        val routeId = activeSessions[sessionId] ?: return
        AppBridgeState.setSinkVolume(routeId, volume)
    }

    override fun onSelectRoute(requestId: Long, sessionId: String, routeId: String) {}

    override fun onDeselectRoute(requestId: Long, sessionId: String, routeId: String) {}

    companion object {
        private const val TAG = "SingularityMediaRoute2"
    }
}
