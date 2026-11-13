package com.kanhaji.singularity.feature.home.components

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HomeFabModeTest {

    @Test
    fun resolveFabMode_pcDisconnected_returnsOffline() {
        val mode = resolveFabMode(isPcConnected = false, isLoggedIn = false, isPcLocked = true)
        assertThat(mode).isEqualTo(HomeFabMode.OFFLINE)
    }

    @Test
    fun resolveFabMode_locked_returnsUnlock() {
        val mode = resolveFabMode(isPcConnected = true, isLoggedIn = false, isPcLocked = true)
        assertThat(mode).isEqualTo(HomeFabMode.UNLOCK)

        val modeLoggedIn = resolveFabMode(isPcConnected = true, isLoggedIn = true, isPcLocked = true)
        assertThat(modeLoggedIn).isEqualTo(HomeFabMode.UNLOCK)
    }

    @Test
    fun resolveFabMode_unlocked_returnsLock() {
        val mode = resolveFabMode(isPcConnected = true, isLoggedIn = false, isPcLocked = false)
        assertThat(mode).isEqualTo(HomeFabMode.LOCK)

        val modeLoggedIn = resolveFabMode(isPcConnected = true, isLoggedIn = true, isPcLocked = false)
        assertThat(modeLoggedIn).isEqualTo(HomeFabMode.LOCK)
    }
}
