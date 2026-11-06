package com.kanhaji.singularity.service.bridge

import android.content.Context
import android.util.Log
import com.kanhaji.singularity.feature.media.MediaControlManager
import com.kanhaji.singularity.feature.media.model.MediaAction
import com.kanhaji.singularity.feature.media.model.MediaState
import com.kanhaji.singularity.service.AuthResultData
import com.kanhaji.singularity.service.PendingAuth
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

object AppBridgeState {
    private const val TAG = "SingularityBridgeState"

    val currentAuthRequest = MutableStateFlow<PendingAuth?>(null)
    val pcLockState = MutableStateFlow<String>("disconnected")
    val pcLoginState = MutableStateFlow<Boolean>(false)
    val isPcConnected = MutableStateFlow<Boolean>(false)
    val isPhoneLocked = MutableStateFlow<Boolean>(false)
    val isCameraStreaming = MutableStateFlow<Boolean>(false)
    val selectedCameraLens = MutableStateFlow<String>("main")
    val pendingCameraAction = AtomicReference<String?>(null)
    val pendingCameraLens = AtomicReference<String?>(null)
    val pendingMediaAction = AtomicReference<MediaAction?>(null)
    val currentMediaState = MutableStateFlow<MediaState?>(null)
    val pendingUnlockRequest = AtomicBoolean(false)
    val pendingLockRequest = AtomicBoolean(false)
    val pendingLogoutRequest = AtomicBoolean(false)
    val pendingLoginRequest = AtomicBoolean(false)
    val pendingPowerAction = AtomicReference<String?>(null)
    val pendingOpenUrl = AtomicReference<String?>(null)
    val fileServerUrl = MutableStateFlow<String>("http://127.0.0.1:8920")
    val lastPacketReceivedMs = AtomicLong(0L)
    val activeConnections = AtomicInteger(0)
    val isActivityVisible = AtomicBoolean(false)

    fun resetPcDisconnected(context: Context? = null) {
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
        pendingOpenUrl.set(null)
        isCameraStreaming.value = false
        pendingCameraAction.set(null)
        pendingCameraLens.set(null)
        pendingMediaAction.set(null)
        currentMediaState.value = null
        if (context != null) {
            MediaControlManager.getInstance(context).clearMedia()
        }
    }

    fun requestPcUnlock() { pendingUnlockRequest.set(true) }
    fun requestPcLock() { pendingLockRequest.set(true) }
    fun requestPcLogout() { pendingLogoutRequest.set(true) }
    fun requestPcLogin() {
        pendingLoginRequest.set(true)
        pendingUnlockRequest.set(true)
    }
    fun requestPowerAction(action: String) { pendingPowerAction.set(action) }
    fun requestOpenUrlOnDesktop(url: String) { pendingOpenUrl.set(url) }
    fun requestStartCamera(lens: String = "main") {
        pendingCameraLens.set(lens)
        selectedCameraLens.value = lens
        pendingCameraAction.set("start")
    }
    fun requestStopCamera() { pendingCameraAction.set("stop") }
    fun requestSwitchLens(lens: String) {
        selectedCameraLens.value = lens
        pendingCameraLens.set(lens)
        pendingCameraAction.set("switch_lens")
    }
}
