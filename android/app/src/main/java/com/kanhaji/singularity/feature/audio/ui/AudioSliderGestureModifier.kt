package com.kanhaji.singularity.feature.audio.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs

fun Modifier.audioSliderGesture(
    key: String,
    isSelected: Boolean,
    totalWidthPx: Float,
    actionZoneWidthPx: Float = 0f,
    onSelect: () -> Unit,
    onDragStateChanged: (Boolean) -> Unit,
    onVolumeAdjust: (Int) -> Unit,
    onVolumeFinished: (Int) -> Unit
): Modifier = this.pointerInput(key, isSelected, totalWidthPx, actionZoneWidthPx) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val downX = down.position.x
        val downY = down.position.y

        if (actionZoneWidthPx > 0f && downX >= totalWidthPx - actionZoneWidthPx) {
            return@awaitEachGesture
        }

        var isDragging = false
        val touchSlop = viewConfiguration.touchSlop
        var currentVol = 0

        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break

            if (change.isConsumed) {
                if (isDragging) {
                    onDragStateChanged(false)
                }
                break
            }

            if (!change.pressed) {
                if (isDragging) {
                    onDragStateChanged(false)
                    onVolumeFinished(currentVol)
                    change.consume()
                } else {
                    val finalDx = abs(change.position.x - downX)
                    val finalDy = abs(change.position.y - downY)
                    if (finalDx <= touchSlop && finalDy <= touchSlop) {
                        if (!isSelected) {
                            onSelect()
                        } else {
                            val vol = AudioSliderHelper.computeVolumePercent(downX, totalWidthPx)
                            onVolumeAdjust(vol)
                            onVolumeFinished(vol)
                        }
                        change.consume()
                    }
                }
                break
            }

            val dx = change.position.x - downX
            val dy = change.position.y - downY

            if (!isDragging) {
                if (abs(dy) > touchSlop && abs(dy) > abs(dx)) {
                    // Vertical scrolling gesture: let parent LazyColumn / scroll handle it completely
                    break
                } else if (abs(dx) > touchSlop && abs(dx) > abs(dy)) {
                    // Horizontal drag gesture: lock into volume slider adjustment
                    isDragging = true
                    onDragStateChanged(true)
                    if (!isSelected) onSelect()
                }
            }

            if (isDragging) {
                change.consume()
                currentVol = AudioSliderHelper.computeVolumePercent(change.position.x, totalWidthPx)
                onVolumeAdjust(currentVol)
            }
        }
    }
}
