package com.kanhaji.singularity.service.bridge

import android.content.Context
import android.content.ContextWrapper
import com.kanhaji.singularity.feature.auth.model.AuthMode
import com.kanhaji.singularity.feature.auth.model.AuthSettings
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class LockTransitionHelperTest {
    private val fakeContext: Context = object : ContextWrapper(null) {}

    private var notifyCalled = false
    private var notifyIsLogin = false
    private var promptCalled = false
    private var promptIsLogin = false
    private var dismissCalled = false

    @Before
    fun setUp() {
        LockTransitionHelper.reset()
        notifyCalled = false; notifyIsLogin = false; promptCalled = false; promptIsLogin = false; dismissCalled = false
        LockTransitionHelper.onNotifyAction = { _, isLogin -> notifyCalled = true; notifyIsLogin = isLogin }
        LockTransitionHelper.onPromptAction = { _, isLogin -> promptCalled = true; promptIsLogin = isLogin }
        LockTransitionHelper.onDismissAction = { dismissCalled = true }
    }

    @After
    fun tearDown() {
        LockTransitionHelper.reset()
        LockTransitionHelper.onNotifyAction = null
        LockTransitionHelper.onPromptAction = null
        LockTransitionHelper.onDismissAction = null
    }

    @Test
    fun testTransitionToLockedPromptModeUnlock() {
        val s = AuthSettings(unlockMode = AuthMode.PROMPT)
        LockTransitionHelper.handleLockTransition(fakeContext, locked = true, loggedIn = true) { s }
        assertTrue(promptCalled); assertFalse(promptIsLogin); assertFalse(notifyCalled); assertFalse(dismissCalled)
        assertEquals(true, LockTransitionHelper.lastLockedState)
    }

    @Test
    fun testTransitionToLockedNotifyModeUnlock() {
        val s = AuthSettings(unlockMode = AuthMode.NOTIFY)
        LockTransitionHelper.handleLockTransition(fakeContext, locked = true, loggedIn = true) { s }
        assertFalse(promptCalled); assertTrue(notifyCalled); assertFalse(notifyIsLogin); assertFalse(dismissCalled)
    }

    @Test
    fun testTransitionToLockedPromptModeLogin() {
        val s = AuthSettings(loginMode = AuthMode.PROMPT)
        LockTransitionHelper.handleLockTransition(fakeContext, locked = true, loggedIn = false) { s }
        assertTrue(promptCalled); assertTrue(promptIsLogin); assertFalse(notifyCalled)
    }

    @Test
    fun testTransitionToLockedNotifyModeLogin() {
        val s = AuthSettings(loginMode = AuthMode.NOTIFY)
        LockTransitionHelper.handleLockTransition(fakeContext, locked = true, loggedIn = false) { s }
        assertTrue(notifyCalled); assertTrue(notifyIsLogin); assertFalse(promptCalled)
    }

    @Test
    fun testTransitionToLockedDenyMode() {
        val s = AuthSettings(unlockMode = AuthMode.DENY)
        LockTransitionHelper.handleLockTransition(fakeContext, locked = true, loggedIn = true) { s }
        assertFalse(promptCalled); assertFalse(notifyCalled); assertFalse(dismissCalled)
    }

    @Test
    fun testTransitionToLockedSnoozed() {
        val s = AuthSettings(unlockMode = AuthMode.PROMPT, snoozeUntilEpochMs = System.currentTimeMillis() + 60000L)
        LockTransitionHelper.handleLockTransition(fakeContext, locked = true, loggedIn = true) { s }
        assertFalse(promptCalled); assertFalse(notifyCalled)
    }

    @Test
    fun testSubsequentLockedPacketsDoNotRetrigger() {
        val s = AuthSettings(unlockMode = AuthMode.PROMPT)
        LockTransitionHelper.handleLockTransition(fakeContext, locked = true, loggedIn = true) { s }
        assertTrue(promptCalled)

        promptCalled = false
        LockTransitionHelper.handleLockTransition(fakeContext, locked = true, loggedIn = true) { s }
        assertFalse(promptCalled)
    }

    @Test
    fun testTransitionFromLockedToUnlockedDismissesPromptAndNotification() {
        val s = AuthSettings(unlockMode = AuthMode.PROMPT)
        LockTransitionHelper.handleLockTransition(fakeContext, locked = true, loggedIn = true) { s }
        assertTrue(promptCalled)

        LockTransitionHelper.handleLockTransition(fakeContext, locked = false, loggedIn = true) { s }
        assertTrue(dismissCalled); assertEquals(false, LockTransitionHelper.lastLockedState)

        dismissCalled = false
        LockTransitionHelper.handleLockTransition(fakeContext, locked = false, loggedIn = true) { s }
        assertFalse(dismissCalled)
    }

    @Test
    fun testResetClearsState() {
        val s = AuthSettings(unlockMode = AuthMode.PROMPT)
        LockTransitionHelper.handleLockTransition(fakeContext, locked = true, loggedIn = true) { s }
        assertEquals(true, LockTransitionHelper.lastLockedState)

        LockTransitionHelper.reset()
        assertNull(LockTransitionHelper.lastLockedState)
        assertNull(LockTransitionHelper.lastLoggedInState)
    }
}
