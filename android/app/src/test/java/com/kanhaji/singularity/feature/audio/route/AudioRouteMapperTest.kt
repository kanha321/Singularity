package com.kanhaji.singularity.feature.audio.route

import com.kanhaji.singularity.feature.audio.model.LaptopAudioSink
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioRouteMapperTest {

    @Test
    fun formatDisplayName_usesNickIfAvailable() {
        val sink = LaptopAudioSink(
            name = "alsa_output.pci.hdmi",
            description = "Family 17h/19h HD Audio Controller",
            nick = "Odyssey G5",
            deviceType = "hdmi",
            isDefault = true,
            volume = 80,
            mute = false
        )
        val name = AudioRouteMapper.formatDisplayName(sink)
        assertEquals("Laptop: Odyssey G5", name)
    }

    @Test
    fun formatDisplayName_doesNotDuplicateLaptopPrefix() {
        val sink = LaptopAudioSink(
            name = "alsa_output.speaker",
            description = "Laptop Internal Speaker",
            nick = "Laptop Speaker",
            deviceType = "speaker",
            isDefault = false,
            volume = 50,
            mute = false
        )
        val name = AudioRouteMapper.formatDisplayName(sink)
        assertEquals("Laptop Speaker", name)
    }

    @Test
    fun formatDisplayName_fallsBackToDescriptionOrName() {
        val sink1 = LaptopAudioSink(
            name = "alsa_output.usb_dac",
            description = "FiiO K3 DAC",
            nick = "",
            deviceType = "headphones",
            isDefault = false,
            volume = 70,
            mute = false
        )
        assertEquals("Laptop: FiiO K3 DAC", AudioRouteMapper.formatDisplayName(sink1))

        val sink2 = LaptopAudioSink(
            name = "raw_output_sink",
            description = "",
            nick = "",
            deviceType = "generic",
            isDefault = false,
            volume = 100,
            mute = false
        )
        assertEquals("Laptop: raw_output_sink", AudioRouteMapper.formatDisplayName(sink2))
    }

    @Test
    fun resolveIconType_classifiesCorrectly() {
        fun make(type: String) = LaptopAudioSink(
            name = "s", description = "d", nick = "n",
            deviceType = type, isDefault = false, volume = 50, mute = false
        )
        assertEquals("display", AudioRouteMapper.resolveIconType(make("hdmi")))
        assertEquals("display", AudioRouteMapper.resolveIconType(make("Monitor")))
        assertEquals("headphones", AudioRouteMapper.resolveIconType(make("headphones")))
        assertEquals("speaker", AudioRouteMapper.resolveIconType(make("Speaker")))
        assertEquals("bluetooth", AudioRouteMapper.resolveIconType(make("bluetooth")))
        assertEquals("laptop", AudioRouteMapper.resolveIconType(make("other")))
    }

    @Test
    fun resolveRouteType_classifiesCorrectly() {
        fun make(type: String) = LaptopAudioSink(
            name = "s", description = "d", nick = "n",
            deviceType = type, isDefault = false, volume = 50, mute = false
        )
        assertEquals(android.media.MediaRoute2Info.TYPE_HDMI, AudioRouteMapper.resolveRouteType(make("hdmi")))
        assertEquals(android.media.MediaRoute2Info.TYPE_HDMI, AudioRouteMapper.resolveRouteType(make("monitor")))
        assertEquals(android.media.MediaRoute2Info.TYPE_REMOTE_SPEAKER, AudioRouteMapper.resolveRouteType(make("speaker")))
        assertEquals(android.media.MediaRoute2Info.TYPE_REMOTE_SPEAKER, AudioRouteMapper.resolveRouteType(make("headphones")))
    }
}

