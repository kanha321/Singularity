package com.kanhaji.singularity.feature.audio.route

import com.kanhaji.core.storage.provider.KSafeProvider
import com.kanhaji.singularity.feature.audio.model.LaptopAudioSink
import com.kanhaji.singularity.service.bridge.AppBridgeState
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject

object AudioRoutePreferences {
    private const val KEY_AUDIO_ROUTE = "preferred_audio_output_route"
    private const val KEY_LOW_LATENCY = "audio_low_latency_mode"

    val isLowLatencyMode = MutableStateFlow(getSavedLowLatencyMode())

    fun saveRoute(route: String?) {
        val target = route ?: "phone"
        runCatching {
            KSafeProvider.prefs.putDirect(KEY_AUDIO_ROUTE, target)
        }
    }

    fun getSavedRoute(): String {
        return runCatching {
            KSafeProvider.prefs.getDirect(KEY_AUDIO_ROUTE, "phone")
        }.getOrDefault("phone")
    }

    fun saveLowLatencyMode(enabled: Boolean) {
        isLowLatencyMode.value = enabled
        runCatching {
            KSafeProvider.prefs.putDirect(KEY_LOW_LATENCY, enabled)
        }
    }

    fun getSavedLowLatencyMode(): Boolean {
        return runCatching {
            KSafeProvider.prefs.getDirect(KEY_LOW_LATENCY, false)
        }.getOrDefault(false)
    }

    fun setLowLatencyMode(enabled: Boolean) {
        saveLowLatencyMode(enabled)
        val cmd = JSONObject().apply {
            put("action", "set_low_latency")
            put("low_latency", enabled)
        }
        AppBridgeState.pendingAudioRouteCommand.set(cmd)
    }

    fun restoreIfAvailable(sinks: List<LaptopAudioSink>) {
        if (!AppBridgeState.isPcConnected.value) return
        val preferred = getSavedRoute()
        if (preferred != "phone" && AppBridgeState.selectedAudioRoute.value != preferred) {
            if (preferred == "singularity_phone_sink" || sinks.any { it.name == preferred }) {
                AppBridgeState.setAudioRoute(preferred)
                return
            }
        }
        if (isLowLatencyMode.value && AppBridgeState.pendingAudioRouteCommand.get() == null) {
            val cmd = JSONObject().apply {
                put("action", "set_low_latency")
                put("low_latency", true)
            }
            AppBridgeState.pendingAudioRouteCommand.set(cmd)
        }
    }
}
