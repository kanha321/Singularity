package com.kanhaji.singularity.feature.player.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import com.kanhaji.singularity.feature.player.state.PlayerSpeedState
import com.kanhaji.singularity.feature.player.state.PlayerSwipeSeekState
import com.kanhaji.singularity.feature.player.state.PlayerVolumeBrightnessState
import com.kanhaji.singularity.feature.player.state.VolumeBrightnessType
import com.kanhaji.singularity.feature.player.util.DragGestureType
import com.kanhaji.singularity.feature.player.util.PlayerGestureHelper
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun Modifier.playerSwipeGestures(
    volumeBrightnessState: PlayerVolumeBrightnessState,
    swipeSeekState: PlayerSwipeSeekState,
    speedState: PlayerSpeedState,
    getInitialBrightness: () -> Int,
    getInitialVolume: () -> Int,
    getCurrentPosition: () -> Long,
    getTotalDuration: () -> Long,
    getCurrentSpeed: () -> Float,
    onDismissControls: () -> Unit
): Modifier = pointerInput(Unit) {
    val touchSlop = viewConfiguration.touchSlop

    coroutineScope {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val downPos = down.position
            val width = size.width.toFloat()
            val height = size.height.toFloat()

            if (!PlayerGestureHelper.isWithinAllowedBounds(downPos.x, width)) {
                return@awaitEachGesture
            }

            var totalDragX = 0f
            var totalDragY = 0f
            var activeDragType = DragGestureType.UNDECIDED
            var isPressAndHold = false
            val isHoldOnLeft = downPos.x < (width / 2f)

            val longPressJob = launch {
                delay(400L)
                if (activeDragType == DragGestureType.UNDECIDED) {
                    isPressAndHold = true
                    onDismissControls()
                    speedState.onHoldStart(isLeft = isHoldOnLeft, currentSpeed = getCurrentSpeed())
                }
            }

            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull() ?: break

                if (!change.pressed) {
                    longPressJob.cancel()
                    if (isPressAndHold) {
                        speedState.onHoldEnd()
                    } else {
                        when (activeDragType) {
                            DragGestureType.VERTICAL_BRIGHTNESS,
                            DragGestureType.VERTICAL_VOLUME -> volumeBrightnessState.onDragEnd()
                            DragGestureType.HORIZONTAL_SEEK -> swipeSeekState.onDragEnd()
                            DragGestureType.UNDECIDED -> Unit
                        }
                    }
                    break
                }

                val dragDelta = change.positionChange()
                totalDragX += dragDelta.x
                totalDragY += dragDelta.y

                if (isPressAndHold) {
                    change.consume()
                    val currentPos = change.position

                    val isHoverLeft = isHoldOnLeft && PlayerGestureHelper.isHoveringEdgeLock(currentPos.x, currentPos.y, width, height, isLeft = true)
                    val isHoverRight = !isHoldOnLeft && PlayerGestureHelper.isHoveringEdgeLock(currentPos.x, currentPos.y, width, height, isLeft = false)
                    speedState.onHoverLock(isHoverLeft, isHoverRight)

                    val isMovingToEdge = if (isHoldOnLeft) currentPos.x < width * 0.18f else currentPos.x > width * 0.82f
                    if (!isMovingToEdge) {
                        val speedDelta = PlayerGestureHelper.calculateSpeedDelta(downPos.y, currentPos.y, height)
                        speedState.onSlideDelta(speedDelta)
                    }
                    continue
                }

                if (activeDragType == DragGestureType.UNDECIDED) {
                    activeDragType = PlayerGestureHelper.classifyDrag(
                        startX = downPos.x,
                        width = width,
                        totalDragX = totalDragX,
                        totalDragY = totalDragY,
                        touchSlop = touchSlop
                    )

                    if (activeDragType != DragGestureType.UNDECIDED) {
                        longPressJob.cancel()
                    }

                    when (activeDragType) {
                        DragGestureType.VERTICAL_BRIGHTNESS -> {
                            volumeBrightnessState.onDragStart(VolumeBrightnessType.BRIGHTNESS, getInitialBrightness(), alignOnRight = true)
                        }
                        DragGestureType.VERTICAL_VOLUME -> {
                            volumeBrightnessState.onDragStart(VolumeBrightnessType.VOLUME, getInitialVolume(), alignOnRight = false)
                        }
                        DragGestureType.HORIZONTAL_SEEK -> {
                            swipeSeekState.onDragStart(getCurrentPosition(), getTotalDuration())
                        }
                        DragGestureType.UNDECIDED -> Unit
                    }
                }

                if (activeDragType != DragGestureType.UNDECIDED) {
                    change.consume()

                    when (activeDragType) {
                        DragGestureType.VERTICAL_BRIGHTNESS -> {
                            val delta = PlayerGestureHelper.calculateVerticalDeltaPercent(totalDragY, height)
                            volumeBrightnessState.onDragDelta(VolumeBrightnessType.BRIGHTNESS, delta)
                        }
                        DragGestureType.VERTICAL_VOLUME -> {
                            val delta = PlayerGestureHelper.calculateVerticalDeltaPercent(totalDragY, height)
                            volumeBrightnessState.onDragDelta(VolumeBrightnessType.VOLUME, delta)
                        }
                        DragGestureType.HORIZONTAL_SEEK -> {
                            val ratio = if (width > 0f) totalDragX / width else 0f
                            swipeSeekState.onDragDelta(ratio)
                        }
                        DragGestureType.UNDECIDED -> Unit
                    }
                }
            }
        }
    }
}
