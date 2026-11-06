package com.kanhaji.singularity.feature.media.session

import com.google.common.truth.Truth.assertThat
import com.kanhaji.singularity.feature.media.model.MediaAction
import com.kanhaji.singularity.feature.media.model.MediaState
import org.junit.Before
import org.junit.Test

class PhoneMediaSessionTrackerTest {

    private lateinit var tracker: PhoneMediaSessionTracker

    @Before
    fun setUp() {
        tracker = PhoneMediaSessionTracker(selfPackageName = "com.kanhaji.singularity")
    }

    private fun createFakeBridge(pkg: String, playing: Boolean, title: String = "Test Title") =
        FakeMediaSessionBridge(pkg, playing, MediaState(
            player = pkg, title = title, artist = "Test Artist", album = "Test Album",
            artUrl = null, status = if (playing) "Playing" else "Paused", positionMs = 1000L,
            durationMs = 5000L, canPlay = true, canPause = true, canNext = true, canPrev = true, canSeek = true
        ))

    @Test
    fun updateSessions_ignoresSelfPackageToPreventEchoLoop() {
        val selfBridge = createFakeBridge("com.kanhaji.singularity", playing = true)
        val youtubeBridge = createFakeBridge("com.google.android.youtube", playing = true, title = "YT Video")

        tracker.updateSessions(listOf(selfBridge, youtubeBridge))

        assertThat(tracker.currentMedia.value?.player).isEqualTo("com.google.android.youtube")
        assertThat(tracker.currentMedia.value?.title).isEqualTo("YT Video")
    }

    @Test
    fun updateSessions_multipleSessions_prioritizesPlayingSession() {
        val spotifyPaused = createFakeBridge("com.spotify.music", playing = false, title = "Spotify Song")
        val youtubePlaying = createFakeBridge("com.google.android.youtube", playing = true, title = "YT Video")

        tracker.updateSessions(listOf(spotifyPaused, youtubePlaying))

        assertThat(tracker.currentMedia.value?.player).isEqualTo("com.google.android.youtube")
        assertThat(tracker.currentMedia.value?.status).isEqualTo("Playing")
    }

    @Test
    fun updateSessions_noPlayingSession_selectsFirstPausedSession() {
        val spotifyPaused = createFakeBridge("com.spotify.music", playing = false, title = "Spotify Song")
        val podcastPaused = createFakeBridge("com.podcast.app", playing = false, title = "Podcast Episode")

        tracker.updateSessions(listOf(spotifyPaused, podcastPaused))

        assertThat(tracker.currentMedia.value?.player).isEqualTo("com.spotify.music")
        assertThat(tracker.currentMedia.value?.status).isEqualTo("Paused")
    }

    @Test
    fun updateSessions_emptyList_clearsCurrentMedia() {
        val youtubePlaying = createFakeBridge("com.google.android.youtube", playing = true)
        tracker.updateSessions(listOf(youtubePlaying))
        assertThat(tracker.currentMedia.value).isNotNull()

        tracker.updateSessions(emptyList())

        assertThat(tracker.currentMedia.value).isNull()
    }

    @Test
    fun executeAction_play_callsTargetPlay() {
        val youtube = createFakeBridge("com.google.android.youtube", playing = false)
        tracker.updateSessions(listOf(youtube))

        val result = tracker.executeAction(MediaAction(player = "", command = "play"))

        assertThat(result).isTrue()
        assertThat(youtube.playCount).isEqualTo(1)
    }

    @Test
    fun executeAction_pause_callsTargetPause() {
        val youtube = createFakeBridge("com.google.android.youtube", playing = true)
        tracker.updateSessions(listOf(youtube))

        val result = tracker.executeAction(MediaAction(player = "", command = "pause"))

        assertThat(result).isTrue()
        assertThat(youtube.pauseCount).isEqualTo(1)
    }

    @Test
    fun executeAction_playPause_togglesCorrectly() {
        val playing = createFakeBridge("com.app.play", playing = true)
        tracker.updateSessions(listOf(playing))
        tracker.executeAction(MediaAction(player = "", command = "play_pause"))
        assertThat(playing.pauseCount).isEqualTo(1)

        val paused = createFakeBridge("com.app.pause", playing = false)
        tracker.updateSessions(listOf(paused))
        tracker.executeAction(MediaAction(player = "", command = "play_pause"))
        assertThat(paused.playCount).isEqualTo(1)
    }

    @Test
    fun executeAction_nextAndPreviousAndSeek_callsAppropriateMethods() {
        val player = createFakeBridge("com.app.music", playing = true)
        tracker.updateSessions(listOf(player))

        tracker.executeAction(MediaAction(player = "", command = "next"))
        assertThat(player.nextCount).isEqualTo(1)

        tracker.executeAction(MediaAction(player = "", command = "previous"))
        assertThat(player.prevCount).isEqualTo(1)

        tracker.executeAction(MediaAction(player = "", command = "seek", positionMs = 42000L))
        assertThat(player.lastSeekPos).isEqualTo(42000L)
    }

    @Test
    fun executeAction_routesToSpecificPlayerIfSpecified() {
        val playerA = createFakeBridge("com.app.a", playing = true)
        val playerB = createFakeBridge("com.app.b", playing = false)
        tracker.updateSessions(listOf(playerA, playerB))

        val result = tracker.executeAction(MediaAction(player = "com.app.b", command = "play"))

        assertThat(result).isTrue()
        assertThat(playerB.playCount).isEqualTo(1)
        assertThat(playerA.playCount).isEqualTo(0)
    }

    @Test
    fun executeAction_unknownCommandOrEmptySessions_returnsFalse() {
        val player = createFakeBridge("com.app.music", playing = true)
        tracker.updateSessions(listOf(player))

        assertThat(tracker.executeAction(MediaAction(player = "", command = "invalid_cmd"))).isFalse()

        tracker.clear()
        assertThat(tracker.executeAction(MediaAction(player = "", command = "play"))).isFalse()
        assertThat(tracker.currentMedia.value).isNull()
    }
}
