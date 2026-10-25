package com.kanhaji.singularity.feature.player.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerGestureHelperTest {

    @Test
    fun isWithinAllowedBounds_withinCenter_returnsTrue() {
        val width = 1000f
        assertTrue(PlayerGestureHelper.isWithinAllowedBounds(500f, width))
        assertTrue(PlayerGestureHelper.isWithinAllowedBounds(60f, width))
        assertTrue(PlayerGestureHelper.isWithinAllowedBounds(940f, width))
    }

    @Test
    fun isWithinAllowedBounds_leftEdgeDeadzone_returnsFalse() {
        val width = 1000f
        assertFalse(PlayerGestureHelper.isWithinAllowedBounds(40f, width))
        assertFalse(PlayerGestureHelper.isWithinAllowedBounds(0f, width))
        assertFalse(PlayerGestureHelper.isWithinAllowedBounds(-10f, width))
    }

    @Test
    fun isWithinAllowedBounds_rightEdgeDeadzone_returnsFalse() {
        val width = 1000f
        assertFalse(PlayerGestureHelper.isWithinAllowedBounds(960f, width))
        assertFalse(PlayerGestureHelper.isWithinAllowedBounds(1000f, width))
        assertFalse(PlayerGestureHelper.isWithinAllowedBounds(1050f, width))
    }

    @Test
    fun isWithinAllowedBounds_zeroWidth_returnsFalse() {
        assertFalse(PlayerGestureHelper.isWithinAllowedBounds(10f, 0f))
    }

    @Test
    fun classifyDrag_belowTouchSlop_returnsUndecided() {
        val res = PlayerGestureHelper.classifyDrag(
            startX = 200f,
            width = 1000f,
            totalDragX = 5f,
            totalDragY = 8f,
            touchSlop = 20f
        )
        assertEquals(DragGestureType.UNDECIDED, res)
    }

    @Test
    fun classifyDrag_verticalDominantLeftHalf_returnsVerticalBrightness() {
        val res = PlayerGestureHelper.classifyDrag(
            startX = 200f,
            width = 1000f,
            totalDragX = 10f,
            totalDragY = 50f,
            touchSlop = 20f
        )
        assertEquals(DragGestureType.VERTICAL_BRIGHTNESS, res)
    }

    @Test
    fun classifyDrag_verticalDominantRightHalf_returnsVerticalVolume() {
        val res = PlayerGestureHelper.classifyDrag(
            startX = 800f,
            width = 1000f,
            totalDragX = 10f,
            totalDragY = -50f,
            touchSlop = 20f
        )
        assertEquals(DragGestureType.VERTICAL_VOLUME, res)
    }

    @Test
    fun classifyDrag_horizontalDominant_returnsHorizontalSeek() {
        val res = PlayerGestureHelper.classifyDrag(
            startX = 300f,
            width = 1000f,
            totalDragX = 60f,
            totalDragY = 15f,
            touchSlop = 20f
        )
        assertEquals(DragGestureType.HORIZONTAL_SEEK, res)
    }

    @Test
    fun classifyDrag_diagonalMovement_returnsUndecided() {
        val res = PlayerGestureHelper.classifyDrag(
            startX = 300f,
            width = 1000f,
            totalDragX = 30f,
            totalDragY = 35f,
            touchSlop = 20f
        )
        assertEquals(DragGestureType.UNDECIDED, res)
    }

    @Test
    fun calculateVerticalDeltaPercent_upwardDrag_returnsPositivePercent() {
        val percent = PlayerGestureHelper.calculateVerticalDeltaPercent(-250f, 1000f)
        assertEquals(25, percent)
    }

    @Test
    fun calculateVerticalDeltaPercent_downwardDrag_returnsNegativePercent() {
        val percent = PlayerGestureHelper.calculateVerticalDeltaPercent(400f, 1000f)
        assertEquals(-40, percent)
    }

    @Test
    fun calculateSpeedDelta_upwardDrag_returnsPositiveDelta() {
        val delta = PlayerGestureHelper.calculateSpeedDelta(500f, 400f, 1000f)
        assertTrue(delta > 0f)
    }

    @Test
    fun isHoveringEdgeLock_leftLockActive_returnsTrue() {
        val hovered = PlayerGestureHelper.isHoveringEdgeLock(50f, 500f, 1000f, 1000f, isLeft = true)
        assertTrue(hovered)
    }

    @Test
    fun isHoveringEdgeLock_outsideVerticalZone_returnsFalse() {
        val hovered = PlayerGestureHelper.isHoveringEdgeLock(50f, 100f, 1000f, 1000f, isLeft = true)
        assertFalse(hovered)
    }
}
