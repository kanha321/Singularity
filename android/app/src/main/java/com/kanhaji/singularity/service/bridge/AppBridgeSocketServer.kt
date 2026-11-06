package com.kanhaji.singularity.service.bridge

import android.content.Context
import android.net.LocalServerSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.ServerSocket

class AppBridgeSocketServer(
    private val socketName: String = "singularity_app",
    private val tcpPort: Int = 2828
) {
    private var localServerSocket: LocalServerSocket? = null
    private var tcpServerSocket: ServerSocket? = null

    fun start(scope: CoroutineScope, context: Context, onTerminate: () -> Unit) {
        scope.launch {
            try {
                val server = LocalServerSocket(socketName)
                localServerSocket = server
                while (isActive) {
                    val client = server.accept()
                    launch {
                        AppBridgeStreamHandler.handleStream(context, client.inputStream, client.outputStream, {
                            scope.launch { delay(100); onTerminate() }
                        }) { client.close() }
                    }
                }
            } catch (_: Exception) {}
        }
        scope.launch {
            try {
                val server = ServerSocket(tcpPort)
                tcpServerSocket = server
                while (isActive) {
                    val client = server.accept()
                    launch {
                        AppBridgeStreamHandler.handleStream(context, client.getInputStream(), client.getOutputStream(), {
                            scope.launch { delay(100); onTerminate() }
                        }) { client.close() }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun close() {
        try { localServerSocket?.close() } catch (_: Exception) {}
        try { tcpServerSocket?.close() } catch (_: Exception) {}
        localServerSocket = null
        tcpServerSocket = null
    }
}

