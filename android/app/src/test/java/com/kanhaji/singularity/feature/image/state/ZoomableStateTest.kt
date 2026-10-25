package com.kanhaji.singularity.feature.image.state

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoomableStateTest {

    private val containerSize = Size(1080f, 1920f)

    @Test
    fun initialState_defaultValues_scaleIsOneAndOffsetIsZero() {
        val state = ZoomableState()
        assertEquals(1.0f, state.scale, 0.001f)
        assertEquals(Offset.Zero, state.offset)
        assertFalse(state.isZoomed)
    }

    @Test
    fun onTransform_zoomIn_increasesScaleClampedToMaxScale() {
        val state = ZoomableState(minScale = 1.0f, maxScale = 5.0f)

        state.onTransform(zoomChange = 2.0f, panChange = Offset.Zero, containerSize = containerSize)
        assertEquals(2.0f, state.scale, 0.001f)
        assertTrue(state.isZoomed)

        // Try zooming beyond maxScale (e.g. 2.0 * 4.0 = 8.0, should clamp to 5.0)
        state.onTransform(zoomChange = 4.0f, panChange = Offset.Zero, containerSize = containerSize)
        assertEquals(5.0f, state.scale, 0.001f)
    }

    @Test
    fun onTransform_zoomOut_decreasesScaleClampedToMinScale() {
        val state = ZoomableState(minScale = 1.0f, maxScale = 5.0f)
        state.onTransform(zoomChange = 2.0f, panChange = Offset.Zero, containerSize = containerSize)

        // Zoom out below 1.05f triggers reset to 1.0f
        state.onTransform(zoomChange = 0.5f, panChange = Offset.Zero, containerSize = containerSize)
        assertEquals(1.0f, state.scale, 0.001f)
        assertFalse(state.isZoomed)
        assertEquals(Offset.Zero, state.offset)
    }

    @Test
    fun onTransform_panning_clampsOffsetToContainerBounds() {
        val state = ZoomableState()
        // Zoom to 2.0x -> maxOffsetX = (1080 * 1.0) / 2 = 540, maxOffsetY = (1920 * 1.0) / 2 = 960
        state.onTransform(zoomChange = 2.0f, panChange = Offset.Zero, containerSize = containerSize)

        // Pan within bounds
        state.onTransform(zoomChange = 1.0f, panChange = Offset(100f, 200f), containerSize = containerSize)
        assertEquals(100f, state.offset.x, 0.001f)
        assertEquals(200f, state.offset.y, 0.001f)

        // Pan beyond bounds -> should clamp
        state.onTransform(zoomChange = 1.0f, panChange = Offset(1000f, 2000f), containerSize = containerSize)
        assertEquals(540f, state.offset.x, 0.001f)
        assertEquals(960f, state.offset.y, 0.001f)
    }

    @Test
    fun onDoubleTap_fromNormal_zoomsToDoubleTapScale() {
        val state = ZoomableState(doubleTapScale = 2.5f)
        val center = Offset(containerSize.width / 2f, containerSize.height / 2f)

        state.onDoubleTap(tapOffset = center, containerSize = containerSize)

        assertEquals(2.5f, state.scale, 0.001f)
        assertTrue(state.isZoomed)
        // Center tap has 0 offset
        assertEquals(0f, state.offset.x, 0.001f)
        assertEquals(0f, state.offset.y, 0.001f)
    }

    @Test
    fun onDoubleTap_fromZoomed_resetsToOne() {
        val state = ZoomableState(doubleTapScale = 2.5f)
        state.onDoubleTap(tapOffset = Offset(100f, 100f), containerSize = containerSize)
        assertTrue(state.isZoomed)

        state.onDoubleTap(tapOffset = Offset(100f, 100f), containerSize = containerSize)
        assertEquals(1.0f, state.scale, 0.001f)
        assertEquals(Offset.Zero, state.offset)
        assertFalse(state.isZoomed)
    }

    @Test
    fun reset_whenZoomedAndPanned_resetsScaleToOneAndZeroOffset() {
        val state = ZoomableState()
        state.onTransform(zoomChange = 3.0f, panChange = Offset(150f, 250f), containerSize = containerSize)
        assertTrue(state.isZoomed)

        state.reset()
        assertEquals(1.0f, state.scale, 0.001f)
        assertEquals(Offset.Zero, state.offset)
        assertFalse(state.isZoomed)
    }
}
