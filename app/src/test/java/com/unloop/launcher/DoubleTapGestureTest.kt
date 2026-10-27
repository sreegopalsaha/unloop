package com.unloop.launcher

import com.unloop.launcher.ui.components.isDoubleTapGesture
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DoubleTapGestureTest {

    @Test
    fun testDoubleTap_withinTimeoutAndSlop_returnsTrue() {
        val result = isDoubleTapGesture(
            firstTapTimeMs = 1000L,
            secondTapTimeMs = 1250L,
            distancePx = 30f,
            maxDoubleTapTimeoutMs = 400L,
            maxSlopPx = 100f
        )
        assertTrue(result)
    }

    @Test
    fun testDoubleTap_timeoutExceeded_returnsFalse() {
        // 500ms interval exceeds 400ms timeout
        val result = isDoubleTapGesture(
            firstTapTimeMs = 1000L,
            secondTapTimeMs = 1500L,
            distancePx = 30f,
            maxDoubleTapTimeoutMs = 400L,
            maxSlopPx = 100f
        )
        assertFalse(result)
    }

    @Test
    fun testDoubleTap_distanceExceedsSlop_returnsFalse() {
        // 150px distance exceeds 100px slop threshold
        val result = isDoubleTapGesture(
            firstTapTimeMs = 1000L,
            secondTapTimeMs = 1200L,
            distancePx = 150f,
            maxDoubleTapTimeoutMs = 400L,
            maxSlopPx = 100f
        )
        assertFalse(result)
    }

    @Test
    fun testDoubleTap_negativeOrZeroInterval_returnsFalse() {
        assertFalse(
            isDoubleTapGesture(
                firstTapTimeMs = 1500L,
                secondTapTimeMs = 1000L,
                distancePx = 10f
            )
        )
        assertFalse(
            isDoubleTapGesture(
                firstTapTimeMs = 1000L,
                secondTapTimeMs = 1000L,
                distancePx = 10f
            )
        )
    }

    @Test
    fun testDoubleTap_exactBoundary_returnsTrue() {
        val result = isDoubleTapGesture(
            firstTapTimeMs = 1000L,
            secondTapTimeMs = 1400L,
            distancePx = 100f,
            maxDoubleTapTimeoutMs = 400L,
            maxSlopPx = 100f
        )
        assertTrue(result)
    }
}
