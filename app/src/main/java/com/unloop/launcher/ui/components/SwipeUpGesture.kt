package com.unloop.launcher.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Pure evaluation function for determining if a drag or flick qualifies as an upward swipe gesture.
 */
fun isSwipeUpGesture(
    totalDragY: Float,
    totalDragX: Float,
    velocityY: Float,
    thresholdPx: Float,
    minFlickDistancePx: Float,
    flickVelocityThresholdPx: Float
): Boolean {
    // Must be predominantly vertical upward movement
    val predominantlyVertical = abs(totalDragY) > abs(totalDragX)
    if (!predominantlyVertical) return false

    // Standard drag distance threshold exceeded
    if (totalDragY < -thresholdPx) return true

    // Quick upward flick with velocity
    if (totalDragY < -minFlickDistancePx && velocityY < -flickVelocityThresholdPx) return true

    return false
}

/**
 * Attaches upward swipe detection to a composable.
 * Uses PointerEventPass.Initial so the gesture is observed before child clickables consume events,
 * allowing child clicks and long-clicks to work unimpeded while still detecting upward swipes.
 */
fun Modifier.onSwipeUp(
    thresholdDp: Dp = 40.dp,
    onSwipeUp: () -> Unit
): Modifier = composed {
    val density = LocalDensity.current
    val thresholdPx = with(density) { thresholdDp.toPx() }
    val minFlickDistancePx = with(density) { 16.dp.toPx() }
    val flickVelocityThresholdPx = with(density) { 600.dp.toPx() }

    pointerInput(onSwipeUp) {
        awaitEachGesture {
            val down = awaitFirstDown(pass = PointerEventPass.Initial, requireUnconsumed = false)
            var totalDragY = 0f
            var totalDragX = 0f
            var isSwipeUpTriggered = false
            val velocityTracker = VelocityTracker()
            velocityTracker.addPosition(down.uptimeMillis, down.position)

            while (true) {
                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                val currentDrag = event.changes.firstOrNull { it.id == down.id } ?: break

                if (!currentDrag.pressed) {
                    // Finger lifted - check for quick upward flick
                    val velocity = velocityTracker.calculateVelocity()
                    if (!isSwipeUpTriggered && isSwipeUpGesture(
                            totalDragY = totalDragY,
                            totalDragX = totalDragX,
                            velocityY = velocity.y,
                            thresholdPx = thresholdPx,
                            minFlickDistancePx = minFlickDistancePx,
                            flickVelocityThresholdPx = flickVelocityThresholdPx
                        )
                    ) {
                        isSwipeUpTriggered = true
                        onSwipeUp()
                    }
                    break
                }

                velocityTracker.addPosition(currentDrag.uptimeMillis, currentDrag.position)
                val delta = currentDrag.position - currentDrag.previousPosition
                totalDragY += delta.y
                totalDragX += delta.x

                // If drag distance threshold reached during active drag
                if (!isSwipeUpTriggered && isSwipeUpGesture(
                        totalDragY = totalDragY,
                        totalDragX = totalDragX,
                        velocityY = 0f,
                        thresholdPx = thresholdPx,
                        minFlickDistancePx = minFlickDistancePx,
                        flickVelocityThresholdPx = flickVelocityThresholdPx
                    )
                ) {
                    isSwipeUpTriggered = true
                    currentDrag.consume()
                    onSwipeUp()
                    break
                }
            }
        }
    }
}
