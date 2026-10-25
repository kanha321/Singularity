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
    fun resolveFabMode_loggedOut_returnsLoginRegardlessOfLockState() {
        // isLoggedIn takes top priority when connected
        val modeLocked = resolveFabMode(isPcConnected = true, isLoggedIn = false, isPcLocked = true)
        assertThat(modeLocked).isEqualTo(HomeFabMode.LOGIN)

        val modeUnlocked = resolveFabMode(isPcConnected = true, isLoggedIn = false, isPcLocked = false)
        assertThat(modeUnlocked).isEqualTo(HomeFabMode.LOGIN)
    }

    @Test
    fun resolveFabMode_loggedInAndLocked_returnsUnlock() {
        val mode = resolveFabMode(isPcConnected = true, isLoggedIn = true, isPcLocked = true)
        assertThat(mode).isEqualTo(HomeFabMode.UNLOCK)
    }

    @Test
    fun resolveFabMode_loggedInAndUnlocked_returnsLock() {
        val mode = resolveFabMode(isPcConnected = true, isLoggedIn = true, isPcLocked = false)
        assertThat(mode).isEqualTo(HomeFabMode.LOCK)
    }
}
