package com.kanhaji.singularity.feature.audio.stream

import com.google.common.truth.Truth.assertThat
import com.kanhaji.singularity.service.bridge.AppBridgeState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AudioStreamClientTest {

    @Before
    fun setUp() {
        AppBridgeState.resetPcDisconnected(null)
        AppBridgeState.isPcConnected.value = true
    }

    @Test
    fun client_observesIsListeningToLaptopStateChanges() = runTest {
        val client = AudioStreamClient()
        val testScope = TestScope()
        client.initialize(testScope)

        AppBridgeState.isListeningToLaptop.value = true
        testScope.advanceUntilIdle()

        // Toggle back to false
        AppBridgeState.isListeningToLaptop.value = false
        testScope.advanceUntilIdle()

        client.release()
        assertThat(AppBridgeState.isListeningToLaptop.value).isFalse()
    }
}

