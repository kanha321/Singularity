package com.kanhaji.singularity.feature.power.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PowerActionTest {

    @Test
    fun actionsFor_loggedIn_returnsAllActions() {
        val actions = PowerAction.actionsFor(isLoggedIn = true)
        assertThat(actions).containsExactly(
            PowerAction.REBOOT,
            PowerAction.SHUTDOWN,
            PowerAction.SUSPEND,
            PowerAction.HIBERNATE,
            PowerAction.LOGOUT
        ).inOrder()
    }

    @Test
    fun actionsFor_loggedOut_returnsOnlyRebootAndShutdown() {
        val actions = PowerAction.actionsFor(isLoggedIn = false)
        assertThat(actions).containsExactly(
            PowerAction.REBOOT,
            PowerAction.SHUTDOWN
        ).inOrder()
    }
}
