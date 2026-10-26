package com.unloop.launcher

import com.unloop.launcher.ui.components.isSwipeUpGesture
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeUpGestureTest {

    private val thresholdPx = 100f
    private val minFlickDistancePx = 40f
    private val flickVelocityThresholdPx = 1200f

    @Test
    fun testSwipeUp_dragDistanceExceeded_returnsTrue() {
        // Dragged up 120px (totalDragY = -120f) with minimal horizontal movement
        val result = isSwipeUpGesture(
            totalDragY = -120f,
            totalDragX = 10f,
            velocityY = 0f,
            thresholdPx = thresholdPx,
            minFlickDistancePx = minFlickDistancePx,
            flickVelocityThresholdPx = flickVelocityThresholdPx
        )
        assertTrue(result)
    }

    @Test
    fun testSwipeDown_dragDistanceExceeded_returnsFalse() {
        // Dragged down 120px (totalDragY = 120f)
        val result = isSwipeUpGesture(
            totalDragY = 120f,
            totalDragX = 5f,
            velocityY = 0f,
            thresholdPx = thresholdPx,
            minFlickDistancePx = minFlickDistancePx,
            flickVelocityThresholdPx = flickVelocityThresholdPx
        )
        assertFalse(result)
    }

    @Test
    fun testSwipe_predominantlyHorizontal_returnsFalse() {
        // Dragged horizontally more than vertically
        val result = isSwipeUpGesture(
            totalDragY = -110f,
            totalDragX = 150f,
            velocityY = 0f,
            thresholdPx = thresholdPx,
            minFlickDistancePx = minFlickDistancePx,
            flickVelocityThresholdPx = flickVelocityThresholdPx
        )
        assertFalse(result)
    }

    @Test
    fun testQuickUpwardFlick_meetsVelocityThreshold_returnsTrue() {
        // Dragged up only 50px (under 100px threshold), but fast upward flick
        val result = isSwipeUpGesture(
            totalDragY = -50f,
            totalDragX = 5f,
            velocityY = -1500f,
            thresholdPx = thresholdPx,
            minFlickDistancePx = minFlickDistancePx,
            flickVelocityThresholdPx = flickVelocityThresholdPx
        )
        assertTrue(result)
    }

    @Test
    fun testTapJitter_underFlickDistance_returnsFalse() {
        // Small tap jitter of -5px with high velocity
        val result = isSwipeUpGesture(
            totalDragY = -5f,
            totalDragX = 2f,
            velocityY = -2000f,
            thresholdPx = thresholdPx,
            minFlickDistancePx = minFlickDistancePx,
            flickVelocityThresholdPx = flickVelocityThresholdPx
        )
        assertFalse(result)
    }

    @Test
    fun testSlowDrag_belowDistanceThreshold_returnsFalse() {
        // Dragged up 60px slowly (not exceeding 100px and low velocity)
        val result = isSwipeUpGesture(
            totalDragY = -60f,
            totalDragX = 4f,
            velocityY = -200f,
            thresholdPx = thresholdPx,
            minFlickDistancePx = minFlickDistancePx,
            flickVelocityThresholdPx = flickVelocityThresholdPx
        )
        assertFalse(result)
    }
}
