package com.kanhaji.singularity.service.bridge

import android.os.Build
import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface

class UdpDiscoveryBeacon(private val port: Int = 2829, private val tcpPort: Int = 2828) {
    companion object {
        private const val TAG = "SingularityBeacon"
    }

    private var beaconJob: Job? = null

    fun start(scope: CoroutineScope) {
        if (beaconJob?.isActive == true) return
        beaconJob = scope.launch(Dispatchers.IO) {
            Log.i(TAG, "Starting UDP discovery beacon sender on port $port...")
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket()
                socket.broadcast = true
                val payload = JSONObject().apply {
                    put("singularity", "beacon")
                    put("device", Build.MODEL)
                    put("port", tcpPort)
                }.toString().toByteArray(Charsets.UTF_8)

                while (isActive && !AppBridgeState.isPcConnected.value) {
                    try {
                        for (addr in getBroadcastAddresses()) {
                            val packet = DatagramPacket(payload, payload.size, addr, port)
                            socket.send(packet)
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "UDP beacon send error: ${e.message}")
                    }
                    delay(3000L)
                }
            } catch (_: CancellationException) {
            } catch (e: Exception) {
                Log.w(TAG, "Failed creating UDP beacon socket: ${e.message}")
            } finally {
                socket?.close()
            }
        }
    }

    fun stop() {
        beaconJob?.cancel()
        beaconJob = null
    }

    private fun getBroadcastAddresses(): List<InetAddress> {
        val targets = mutableListOf<InetAddress>()
        try {
            targets.add(InetAddress.getByName("255.255.255.255"))
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                for (addr in iface.interfaceAddresses) {
                    val bcast = addr.broadcast
                    if (bcast != null) targets.add(bcast)
                }
            }
        } catch (_: Exception) {}
        return targets.distinct()
    }
}

