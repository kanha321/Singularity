package com.kanhaji.singularity.feature.audio.ui

import android.content.Context
import android.content.ContextWrapper
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppNameResolverTest {
    private val fakeContext: Context = object : ContextWrapper(null) {}

    @Test
    fun resolve_nullOrBlank_returnsNull() {
        assertThat(AppNameResolver.resolve(fakeContext, null)).isNull()
        assertThat(AppNameResolver.resolve(fakeContext, "")).isNull()
        assertThat(AppNameResolver.resolve(fakeContext, "   ")).isNull()
    }

    @Test
    fun resolve_plainNameWithoutDot_returnsAsIs() {
        assertThat(AppNameResolver.resolve(fakeContext, "Thorium")).isEqualTo("Thorium")
        assertThat(AppNameResolver.resolve(fakeContext, "Firefox")).isEqualTo("Firefox")
        assertThat(AppNameResolver.resolve(fakeContext, "Spotify")).isEqualTo("Spotify")
    }

    @Test
    fun resolve_packageFallback_extractsCapitalizedSegment() {
        assertThat(AppNameResolver.resolve(fakeContext, "com.spotify.music")).isEqualTo("Music")
        assertThat(AppNameResolver.resolve(fakeContext, "com.google.android.youtube")).isEqualTo("Youtube")
        assertThat(AppNameResolver.resolve(fakeContext, "org.videolan.vlc")).isEqualTo("Vlc")
    }
}
