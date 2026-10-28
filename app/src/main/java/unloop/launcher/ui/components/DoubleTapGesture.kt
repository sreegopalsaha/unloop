package unloop.launcher.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Pure evaluation function for determining if two consecutive taps qualify as a double tap gesture.
 */
fun isDoubleTapGesture(
    firstTapTimeMs: Long,
    secondTapTimeMs: Long,
    distancePx: Float,
    maxDoubleTapTimeoutMs: Long = 400L,
    maxSlopPx: Float = 100f
): Boolean {
    val interval = secondTapTimeMs - firstTapTimeMs
    if (interval <= 0 || interval > maxDoubleTapTimeoutMs) return false
    if (distancePx > maxSlopPx) return false
    return true
}

/**
 * Attaches double-tap gesture detection to a composable.
 * Only triggers if taps are not consumed by child clickables (e.g., app items or dock buttons).
 */
fun Modifier.onDoubleTap(
    onDoubleTap: () -> Unit
): Modifier = pointerInput(onDoubleTap) {
    detectTapGestures(
        onDoubleTap = {
            onDoubleTap()
        }
    )
}
