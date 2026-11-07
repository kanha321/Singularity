package com.kanhaji.singularity.feature.audio.model

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LaptopAudioSinkTest {

    @Test
    fun testFromJsonAndToJsonRoundtrip() {
        val json = JSONObject().apply {
            put("name", "alsa_output.pci-0000_01_00.1.hdmi-stereo")
            put("description", "AD107 High Definition Audio Controller [Odyssey G5]")
            put("nick", "Odyssey G5")
            put("device_type", "hdmi")
            put("is_default", true)
            put("volume", 100)
            put("mute", false)
        }
        val sink = LaptopAudioSink.fromJson(json)
        assertEquals("alsa_output.pci-0000_01_00.1.hdmi-stereo", sink.name)
        assertEquals("Odyssey G5", sink.nick)
        assertEquals("hdmi", sink.deviceType)
        assertTrue(sink.isDefault)
        assertEquals(100, sink.volume)
        assertFalse(sink.mute)

        val outJson = sink.toJson()
        assertEquals(sink.name, outJson.getString("name"))
        assertEquals(sink.nick, outJson.getString("nick"))
        assertEquals(sink.deviceType, outJson.getString("device_type"))
        assertEquals(sink.isDefault, outJson.getBoolean("is_default"))
        assertEquals(sink.volume, outJson.getInt("volume"))
        assertEquals(sink.mute, outJson.getBoolean("mute"))
    }

    @Test
    fun testDefaultsWhenFieldsMissing() {
        val empty = JSONObject()
        val sink = LaptopAudioSink.fromJson(empty)
        assertEquals("", sink.name)
        assertEquals("", sink.description)
        assertEquals("", sink.nick)
        assertEquals("generic", sink.deviceType)
        assertFalse(sink.isDefault)
        assertEquals(100, sink.volume)
        assertFalse(sink.mute)
    }

    @Test
    fun testVolumeClamping() {
        val over = JSONObject().put("volume", 250)
        assertEquals(150, LaptopAudioSink.fromJson(over).volume)

        val under = JSONObject().put("volume", -20)
        assertEquals(0, LaptopAudioSink.fromJson(under).volume)
    }
}

