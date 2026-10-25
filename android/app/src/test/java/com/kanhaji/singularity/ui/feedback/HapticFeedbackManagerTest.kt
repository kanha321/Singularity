package com.kanhaji.singularity.ui.feedback

import android.view.HapticFeedbackConstants
import android.view.View
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

class HapticFeedbackManagerTest {

    private var performHapticCalls = 0
    private var lastConstantPassed = -1

    @Before
    fun setUp() {
        HapticFeedbackManager.resetForTesting()
        performHapticCalls = 0
        lastConstantPassed = -1
    }

    private fun createFakeView(): View {
        val fake = object : Any() {
            fun performHapticFeedback(constant: Int): Boolean {
                performHapticCalls++
                lastConstantPassed = constant
                return true
            }
        }
        val dummyView = Proxy.newProxyInstance(
            View::class.java.classLoader,
            emptyArray()
        ) { _, method, args ->
            if (method.name == "performHapticFeedback") {
                val constant = args?.get(0) as? Int ?: -1
                fake.performHapticFeedback(constant)
            } else {
                null
            }
        }
        // Since View is an open class in Android SDK and might not be an interface,
        // we can test directly via a subclass or subclass of View if mockable
        return TestFakeView { constant, _ ->
            performHapticCalls++
            lastConstantPassed = constant
            true
        }
    }

    private class TestFakeView(
        val onHaptic: (Int, Int) -> Boolean
    ) : View(null) {
        override fun performHapticFeedback(feedbackConstant: Int): Boolean {
            return onHaptic(feedbackConstant, 0)
        }

        override fun performHapticFeedback(feedbackConstant: Int, flags: Int): Boolean {
            return onHaptic(feedbackConstant, flags)
        }
    }

    @Test
    fun performVirtualKey_withNullView_returnsFalse() {
        val result = HapticFeedbackManager.performVirtualKey(null)
        assertThat(result).isFalse()
    }

    @Test
    fun performVirtualKey_withValidView_triggersHaptic() {
        val view = createFakeView()
        val result = HapticFeedbackManager.performVirtualKey(view)

        assertThat(result).isTrue()
        assertThat(performHapticCalls).isEqualTo(1)
        assertThat(lastConstantPassed).isEqualTo(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    @Test
    fun performVirtualKey_calledTwiceWithinDebounce_debouncesSecondCall() {
        val view = createFakeView()
        val first = HapticFeedbackManager.performVirtualKey(view)
        val second = HapticFeedbackManager.performVirtualKey(view)

        assertThat(first).isTrue()
        assertThat(second).isFalse()
        assertThat(performHapticCalls).isEqualTo(1)
    }

    @Test
    fun performVirtualKey_whenSuppressed_returnsFalse() {
        val view = createFakeView()
        HapticFeedbackManager.suppressNext(500L)

        val result = HapticFeedbackManager.performVirtualKey(view)
        assertThat(result).isFalse()
        assertThat(performHapticCalls).isEqualTo(0)
    }

    @Test
    fun performVirtualKey_afterSuppressionExpires_triggersHaptic() {
        val view = createFakeView()
        HapticFeedbackManager.suppressNext(10L)
        Thread.sleep(25L)

        val result = HapticFeedbackManager.performVirtualKey(view)
        assertThat(result).isTrue()
        assertThat(performHapticCalls).isEqualTo(1)
    }
}
