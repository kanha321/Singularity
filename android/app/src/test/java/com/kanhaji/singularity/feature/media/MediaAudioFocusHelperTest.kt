package com.kanhaji.singularity.feature.media

import android.media.AudioManager
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MediaAudioFocusHelperTest {

    private fun createHelper(
        onPause: () -> Unit = {},
        onPlay: () -> Unit = {},
        isMusicActive: () -> Boolean = { false },
        postDelayed: (Runnable, Long) -> Unit = { _, _ -> },
        removeCallbacks: (Runnable) -> Unit = {}
    ) = MediaAudioFocusHelper(
        audioManager = null,
        onPauseRequested = onPause,
        onPlayRequested = onPlay,
        isMusicActiveProvider = isMusicActive,
        postDelayed = postDelayed,
        removeCallbacks = removeCallbacks
    )

    @Test
    fun onAudioFocusLoss_triggersPauseAndSetsFocusLossFlag() {
        var pauseCalled = false
        var playCalled = false
        val helper = createHelper(onPause = { pauseCalled = true }, onPlay = { playCalled = true })
        helper.hasFocus = true

        helper.focusChangeListener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS)
        assertThat(pauseCalled).isTrue()
        assertThat(playCalled).isFalse()
        assertThat(helper.hasFocus).isFalse()
        assertThat(helper.wasPausedByFocusLoss).isTrue()
    }

    @Test
    fun onAudioFocusLossTransient_triggersPauseAndSetsFocusLossFlag() {
        var pauseCalled = false
        val helper = createHelper(onPause = { pauseCalled = true })
        helper.hasFocus = true

        helper.focusChangeListener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        assertThat(pauseCalled).isTrue()
        assertThat(helper.hasFocus).isFalse()
        assertThat(helper.wasPausedByFocusLoss).isTrue()
    }

    @Test
    fun onAudioFocusLossTransientCanDuck_doesNotTriggerPause() {
        var pauseCalled = false
        val helper = createHelper(onPause = { pauseCalled = true })
        helper.hasFocus = true

        helper.focusChangeListener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)
        assertThat(pauseCalled).isFalse()
        assertThat(helper.hasFocus).isTrue()
        assertThat(helper.wasPausedByFocusLoss).isFalse()
    }

    @Test
    fun onAudioFocusGain_afterFocusLoss_triggersPlayAndClearsFlag() {
        var playCalled = false
        val helper = createHelper(onPlay = { playCalled = true })
        helper.wasPausedByFocusLoss = true
        helper.hasFocus = false

        helper.focusChangeListener.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)
        assertThat(playCalled).isTrue()
        assertThat(helper.hasFocus).isTrue()
        assertThat(helper.wasPausedByFocusLoss).isFalse()
    }

    @Test
    fun onAudioFocusGain_withoutPriorFocusLoss_doesNotTriggerPlay() {
        var playCalled = false
        val helper = createHelper(onPlay = { playCalled = true })
        helper.wasPausedByFocusLoss = false

        helper.focusChangeListener.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)
        assertThat(playCalled).isFalse()
        assertThat(helper.hasFocus).isTrue()
    }

    @Test
    fun resumeWatcher_whenMusicIsActive_doesNotTriggerPlay() {
        var playCalled = false
        var scheduled = false
        val helper = createHelper(
            onPlay = { playCalled = true },
            isMusicActive = { true },
            postDelayed = { _, _ -> scheduled = true }
        )
        helper.wasPausedByFocusLoss = true

        helper.resumeCheckRunnable.run()
        assertThat(playCalled).isFalse()
        assertThat(helper.wasPausedByFocusLoss).isTrue()
        assertThat(helper.inactiveCheckCount).isEqualTo(0)
        assertThat(scheduled).isTrue()
    }

    @Test
    fun resumeWatcher_triggersPlayWhenInactiveTwice() {
        var playCalled = false
        val helper = createHelper(onPlay = { playCalled = true })
        helper.wasPausedByFocusLoss = true

        helper.resumeCheckRunnable.run()
        assertThat(playCalled).isFalse()
        assertThat(helper.inactiveCheckCount).isEqualTo(1)

        helper.resumeCheckRunnable.run()
        assertThat(playCalled).isTrue()
        assertThat(helper.wasPausedByFocusLoss).isFalse()
        assertThat(helper.inactiveCheckCount).isEqualTo(0)
    }

    @Test
    fun abandonFocus_clearsStateWhenRequested() {
        var cancelled = false
        val helper = createHelper(removeCallbacks = { cancelled = true })
        helper.wasPausedByFocusLoss = true
        helper.abandonFocus(clearFocusLoss = true)

        assertThat(helper.wasPausedByFocusLoss).isFalse()
        assertThat(cancelled).isTrue()
    }
}
