package com.kanhaji.singularity.feature.audio.qs

import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.kanhaji.singularity.R
import com.kanhaji.singularity.service.bridge.AppBridgeState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine

class LaptopVolumeTileService : TileService() {

    private var job: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        job?.cancel()
        job = CoroutineScope(Dispatchers.Main).launch {
            combine(
                AppBridgeState.isPcConnected,
                AppBridgeState.availableLaptopAudioSinks
            ) { isConnected, sinks ->
                Pair(isConnected, sinks)
            }.collect { (isConnected, sinks) ->
                updateTileState(isConnected, sinks)
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
        val sinks = AppBridgeState.availableLaptopAudioSinks.value
        val target = sinks.find { it.isDefault } ?: sinks.firstOrNull() ?: return
        AppBridgeState.setSinkMute(target.name, !target.mute)
    }

    private fun updateTileState(
        isConnected: Boolean,
        sinks: List<com.kanhaji.singularity.feature.audio.model.LaptopAudioSink>
    ) {
        val tile = qsTile ?: return
        val target = sinks.find { it.isDefault } ?: sinks.firstOrNull()

        if (!isConnected || target == null) {
            tile.state = Tile.STATE_UNAVAILABLE
            tile.label = "Laptop Vol"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = "Disconnected"
            tile.icon = Icon.createWithResource(this, R.drawable.ic_volume_down)
        } else {
            tile.state = if (target.mute) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
            tile.label = "Laptop Vol"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = if (target.mute) "Muted" else "${target.volume}% (${target.nick})"
            }
            val iconRes = if (target.mute) R.drawable.ic_volume_mute else R.drawable.ic_volume_up
            tile.icon = Icon.createWithResource(this, iconRes)
        }
        tile.updateTile()
    }
}

