package com.unloop.launcher.service

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.unloop.launcher.data.PreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ShortVideoBlockerAccessibilityService : AccessibilityService() {

    private lateinit var prefs: PreferencesManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastActionTime = 0L

    companion object {
        private const val DEBOUNCE_MS = 1000L
        private const val ACTION_DELAY_MS = 500L

        private val _currentForegroundApp = MutableStateFlow<String?>(null)
        val currentForegroundApp: StateFlow<String?> = _currentForegroundApp.asStateFlow()

        var isServiceConnected: Boolean = false
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = PreferencesManager.getInstance(this)
        isServiceConnected = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val pkg = event.packageName?.toString() ?: return

        // Update foreground application tracking for Focus Mode and Digital Wellbeing
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            _currentForegroundApp.value = pkg
        }

        if (!::prefs.isInitialized) {
            prefs = PreferencesManager.getInstance(this)
        }

        // Single Global Short-Video Blocker Toggle Check
        if (!prefs.isShortVideoBlockerEnabled()) {
            return
        }

        // Only inspect supported target video apps
        if (!ShortVideoDetector.SUPPORTED_PACKAGES.contains(pkg)) {
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastActionTime < DEBOUNCE_MS) {
            return
        }

        val rootNode = try {
            rootInActiveWindow
        } catch (e: Exception) {
            null
        } ?: return

        val isShortVideo = ShortVideoDetector.isShortVideoInterface(rootNode, pkg)
        if (isShortVideo) {
            lastActionTime = now

            // 500ms delay to ensure transition stability before popping the modal viewer
            mainHandler.postDelayed({
                val performed = performGlobalAction(GLOBAL_ACTION_BACK)
                if (performed) {
                    Toast.makeText(
                        applicationContext,
                        "Short videos blocked by Unloop",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }, ACTION_DELAY_MS)
        }
    }

    override fun onInterrupt() {
        // Accessibility service interrupted by system
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceConnected = false
    }
}
