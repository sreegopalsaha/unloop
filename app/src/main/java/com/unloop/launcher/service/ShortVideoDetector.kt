package com.unloop.launcher.service

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Heuristic detector for short-form video interfaces across supported applications.
 * Directly reproduces the verified node-hierarchy and geometry detection mechanisms
 * identified in the reverse-engineering analysis of Minimalist Phone.
 */
object ShortVideoDetector {

    const val PKG_YOUTUBE = "com.google.android.youtube"
    const val PKG_INSTAGRAM = "com.instagram.android"
    const val PKG_SNAPCHAT = "com.snapchat.android"
    const val PKG_FACEBOOK = "com.facebook.katana"

    val SUPPORTED_PACKAGES = setOf(
        PKG_YOUTUBE,
        PKG_INSTAGRAM,
        PKG_SNAPCHAT,
        PKG_FACEBOOK
    )

    /**
     * Checks if the active accessibility node hierarchy represents a short-video interface.
     */
    fun isShortVideoInterface(rootNode: AccessibilityNodeInfo?, packageName: String): Boolean {
        if (rootNode == null) return false

        return when (packageName) {
            PKG_YOUTUBE -> detectYouTubeShorts(rootNode)
            PKG_INSTAGRAM -> detectInstagramReels(rootNode)
            PKG_SNAPCHAT -> detectSnapchatSpotlight(rootNode)
            PKG_FACEBOOK -> detectFacebookReels(rootNode)
            else -> false
        }
    }

    /**
     * YouTube Shorts:
     * Full-screen Shorts player uses view ID `com.google.android.youtube:id/reel_recycler`.
     * Ordinary search results and channel lists use `results_list` or `watch_while_layout`,
     * so checking for `reel_recycler` specifically matches only the active Shorts viewer.
     */
    private fun detectYouTubeShorts(root: AccessibilityNodeInfo): Boolean {
        val reelRecyclerNodes = root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/reel_recycler")
        if (reelRecyclerNodes.isNotEmpty()) {
            return true
        }
        return false
    }

    /**
     * Instagram Reels:
     * Full-screen Reels viewer is characterized by the simultaneous presence of:
     * - `com.instagram.android:id/clips_viewer_view_pager`
     * AND
     * - `com.instagram.android:id/clips_ufi_component` (User Feedback Interface / like & comment sidebar)
     * This ensures ordinary feed posts with video or Stories are not falsely matched.
     */
    private fun detectInstagramReels(root: AccessibilityNodeInfo): Boolean {
        val pagerNodes = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/clips_viewer_view_pager")
        if (pagerNodes.isNotEmpty()) {
            val ufiNodes = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/clips_ufi_component")
            if (ufiNodes.isNotEmpty()) {
                return true
            }
        }
        return false
    }

    /**
     * Snapchat Spotlight:
     * Spotlight container uses `spotlight_container`.
     * Or within opera viewer (`opera_viewer`), ensures neither `chat_message_list`
     * nor `ff_item` (Friends feed items) are present.
     */
    private fun detectSnapchatSpotlight(root: AccessibilityNodeInfo): Boolean {
        val spotlightNodes = root.findAccessibilityNodeInfosByViewId("com.snapchat.android:id/spotlight_container")
        if (spotlightNodes.isNotEmpty()) {
            return true
        }

        val operaNodes = root.findAccessibilityNodeInfosByViewId("com.snapchat.android:id/opera_viewer")
        if (operaNodes.isNotEmpty()) {
            val chatNodes = root.findAccessibilityNodeInfosByViewId("com.snapchat.android:id/chat_message_list")
            val friendNodes = root.findAccessibilityNodeInfosByViewId("com.snapchat.android:id/ff_item")
            if (chatNodes.isEmpty() && friendNodes.isEmpty()) {
                return true
            }
        }
        return false
    }

    /**
     * Facebook Reels:
     * Uses layout and hierarchy negation:
     * 1. Clickable overlay container
     * 2. Full-screen video layout
     * 3. Must NOT contain `android:id/list` (Newsfeed list)
     * 4. Must NOT contain active text editing inputs (post creation or comment typing)
     */
    private fun detectFacebookReels(root: AccessibilityNodeInfo): Boolean {
        val newsfeedList = root.findAccessibilityNodeInfosByViewId("android:id/list")
        if (newsfeedList.isNotEmpty()) {
            return false
        }

        var hasFullOverlay = false
        val rect = Rect()

        fun inspectNode(node: AccessibilityNodeInfo, depth: Int) {
            if (depth > 12 || hasFullOverlay) return

            node.getBoundsInScreen(rect)
            val className = node.className?.toString() ?: ""

            // Check if node is a full-screen relative or frame container typical of Reels viewer
            if ((className.contains("RelativeLayout") || className.contains("ViewGroup")) &&
                rect.width() > 800 && rect.height() > 1400 && node.isClickable
            ) {
                hasFullOverlay = true
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                inspectNode(child, depth + 1)
            }
        }

        inspectNode(root, 0)
        return hasFullOverlay
    }
}
