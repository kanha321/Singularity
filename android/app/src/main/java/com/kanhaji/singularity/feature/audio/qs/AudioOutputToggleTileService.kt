package com.kanhaji.singularity.feature.audio.qs

import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.kanhaji.singularity.R
import com.kanhaji.singularity.feature.audio.model.LaptopAudioSink
import com.kanhaji.singularity.service.bridge.AppBridgeState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine

object AudioOutputToggleLogic {
    fun resolveNextRoute(currentRoute: String?, defaultSinkName: String?): String? {
        if (defaultSinkName == null) return null
        return if (currentRoute == "phone" || currentRoute == null) {
            defaultSinkName
        } else {
            "phone"
        }
    }

    fun isRoutingToLaptop(currentRoute: String?): Boolean {
        return currentRoute != null && currentRoute != "phone"
    }

    fun formatSubtitle(isLaptop: Boolean, activeSinkNick: String?): String {
        return if (isLaptop) {
            if (!activeSinkNick.isNullOrBlank()) "Laptop: $activeSinkNick" else "Laptop Speaker"
        } else {
            "Phone Speaker"
        }
    }
}

class AudioOutputToggleTileService : TileService() {

    private var job: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        job?.cancel()
        job = CoroutineScope(Dispatchers.Main).launch {
            combine(
                AppBridgeState.isPcConnected,
                AppBridgeState.selectedAudioRoute,
                AppBridgeState.availableLaptopAudioSinks
            ) { isConnected, currentRoute, sinks ->
                Triple(isConnected, currentRoute, sinks)
            }.collect { (isConnected, currentRoute, sinks) ->
                updateTileState(isConnected, currentRoute, sinks)
            }
        }
    }

    override fun onStopListening() {
        job?.cancel()
        job = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        if (!AppBridgeState.isPcConnected.value) return

        val sinks = AppBridgeState.availableLaptopAudioSinks.value
        val defaultSink = sinks.find { it.isDefault } ?: sinks.firstOrNull() ?: return
        val currentRoute = AppBridgeState.selectedAudioRoute.value
        val nextRoute = AudioOutputToggleLogic.resolveNextRoute(currentRoute, defaultSink.name)

        if (nextRoute != null) {
            AppBridgeState.setAudioRoute(nextRoute)
        }
    }

    private fun updateTileState(
        isConnected: Boolean,
        currentRoute: String?,
        sinks: List<LaptopAudioSink>
    ) {
        val tile = qsTile ?: return
        val defaultSink = sinks.find { it.isDefault } ?: sinks.firstOrNull()

        if (!isConnected || defaultSink == null) {
            tile.state = Tile.STATE_UNAVAILABLE
            tile.label = "Audio Output"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = "Disconnected"
            tile.icon = Icon.createWithResource(this, R.drawable.ic_laptop_speaker)
        } else {
            val isLaptop = AudioOutputToggleLogic.isRoutingToLaptop(currentRoute)
            tile.state = if (isLaptop) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            tile.label = "Audio Output"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val sinkNick = sinks.find { it.name == currentRoute }?.nick ?: defaultSink.nick
                tile.subtitle = AudioOutputToggleLogic.formatSubtitle(isLaptop, sinkNick)
            }
            val iconRes = if (isLaptop) R.drawable.ic_laptop_speaker else R.drawable.ic_phone_speaker
            tile.icon = Icon.createWithResource(this, iconRes)
        }
        tile.updateTile()
    }
}

