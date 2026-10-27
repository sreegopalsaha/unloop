package com.unloop.launcher.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
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
        private const val ACTION_DELAY_MS = 400L

        private val _currentForegroundApp = MutableStateFlow<String?>(null)
        val currentForegroundApp: StateFlow<String?> = _currentForegroundApp.asStateFlow()

        var isServiceConnected: Boolean = false
            private set

        private var instance: ShortVideoBlockerAccessibilityService? = null

        /**
         * Locks the device using Android's accessibility global action.
         * Returns true if the action was successfully dispatched.
         */
        fun lockScreen(): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                instance?.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) ?: false
            } else {
                false
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
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

        // Check if Short-Video Blocker is enabled in preferences
        if (!prefs.isShortVideoBlockerEnabled()) {
            return
        }

        // Only inspect supported target short-video apps
        if (!ShortVideoDetector.SUPPORTED_PACKAGES.contains(pkg)) {
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastActionTime < DEBOUNCE_MS) {
            return
        }

        val rootNode = (try {
            rootInActiveWindow
        } catch (_: Exception) {
            null
        }) ?: event.source ?: return

        val isShortVideo = ShortVideoDetector.isShortVideoInterface(rootNode, pkg)
        if (isShortVideo) {
            lastActionTime = now

            // Delay to allow UI transition stability before exiting short-video player
            mainHandler.postDelayed({
                val currentRoot = try {
                    rootInActiveWindow
                } catch (_: Exception) {
                    null
                }

                // Verify user is still in the target package and still in a short-video interface
                val stillInTargetApp = _currentForegroundApp.value == pkg
                val stillInShorts = if (currentRoot != null) {
                    ShortVideoDetector.isShortVideoInterface(currentRoot, pkg)
                } else {
                    stillInTargetApp
                }

                if (stillInTargetApp && stillInShorts) {
                    val performed = performGlobalAction(GLOBAL_ACTION_BACK)
                    if (performed) {
                        Toast.makeText(
                            applicationContext,
                            "Short videos blocked by Unloop",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        // Fallback to launcher home screen if back action fails
                        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                            addCategory(Intent.CATEGORY_HOME)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        startActivity(homeIntent)
                    }
                }
            }, ACTION_DELAY_MS)
        }
    }

    override fun onInterrupt() {
        // Accessibility service interrupted by system
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (instance == this) {
            instance = null
        }
        isServiceConnected = false
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
        isServiceConnected = false
    }
}
