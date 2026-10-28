package unloop.launcher.service

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Heuristic detector for short-form video interfaces across supported applications:
 * - YouTube Shorts
 * - Instagram Reels
 * - Snapchat Spotlight
 * - Facebook Reels
 *
 * Specifically targets full-screen short video player containers while preserving
 * standard feeds, long-form videos, stories, and private messaging.
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

    private val YOUTUBE_SHORTS_VIEW_IDS = listOf(
        "com.google.android.youtube:id/reel_recycler",
        "com.google.android.youtube:id/shorts_container",
        "com.google.android.youtube:id/reel_player_page_tree",
        "com.google.android.youtube:id/reel_watch_fragment",
        "com.google.android.youtube:id/reel_player_view"
    )

    private val INSTAGRAM_REELS_VIEW_IDS = listOf(
        "com.instagram.android:id/clips_viewer_view_pager",
        "com.instagram.android:id/clips_video_container",
        "com.instagram.android:id/clips_swipe_refresh_layout"
    )

    private val SNAPCHAT_SPOTLIGHT_VIEW_IDS = listOf(
        "com.snapchat.android:id/spotlight_container",
        "com.snapchat.android:id/spotlight_page_layout",
        "com.snapchat.android:id/spotlight_tray_root",
        "com.snapchat.android:id/spotlight_fullscreen_layout",
        "com.snapchat.android:id/spotlight_action_menu"
    )

    private val FACEBOOK_REELS_VIEW_IDS = listOf(
        "com.facebook.katana:id/reels_viewer_fragment_container",
        "com.facebook.katana:id/reel_viewer_container",
        "com.facebook.katana:id/fb_shorts_viewer_container",
        "com.facebook.katana:id/fb_shorts_native_in_feed_unit",
        "com.facebook.katana:id/reels_player_view",
        "com.facebook.katana:id/fb_shorts_video_container",
        "com.facebook.katana:id/fb_shorts_root"
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
     * Full-screen Shorts player uses dedicated reel recycler / player container view IDs.
     * Preserves standard watch player (watch_while_layout / player_view) and home feeds.
     */
    private fun detectYouTubeShorts(root: AccessibilityNodeInfo): Boolean {
        for (viewId in YOUTUBE_SHORTS_VIEW_IDS) {
            val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
            if (nodes.isNotEmpty()) {
                return true
            }
        }
        return false
    }

    /**
     * Instagram Reels:
     * Full-screen Reels viewer is characterized by clips viewer containers (`clips_viewer_view_pager`,
     * `clips_video_container`).
     * Preserves Direct Messages (`direct_thread`, `message_list`), standard stories, and feeds.
     */
    private fun detectInstagramReels(root: AccessibilityNodeInfo): Boolean {
        // Strictly preserve Direct Messaging / private chats
        if (root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/direct_thread").isNotEmpty() ||
            root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/message_list").isNotEmpty()
        ) {
            return false
        }

        for (viewId in INSTAGRAM_REELS_VIEW_IDS) {
            val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
            if (nodes.isNotEmpty()) {
                return true
            }
        }
        return false
    }

    /**
     * Snapchat Spotlight:
     * Spotlight container uses dedicated spotlight view IDs.
     * Strictly preserves direct chats (`chat_message_list`, `chat_input_text_view`) and friend Snaps.
     */
    private fun detectSnapchatSpotlight(root: AccessibilityNodeInfo): Boolean {
        // Strictly preserve friend chats and messages
        if (root.findAccessibilityNodeInfosByViewId("com.snapchat.android:id/chat_message_list").isNotEmpty() ||
            root.findAccessibilityNodeInfosByViewId("com.snapchat.android:id/chat_input_text_view").isNotEmpty()
        ) {
            return false
        }

        for (viewId in SNAPCHAT_SPOTLIGHT_VIEW_IDS) {
            val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
            if (nodes.isNotEmpty()) {
                return true
            }
        }

        // Secondary check within opera viewer: only match if Spotlight indicators are present
        val operaNodes = root.findAccessibilityNodeInfosByViewId("com.snapchat.android:id/opera_viewer")
        if (operaNodes.isNotEmpty()) {
            val spotlightTextNodes = root.findAccessibilityNodeInfosByText("Spotlight")
            if (spotlightTextNodes.isNotEmpty()) {
                return true
            }
        }

        return false
    }

    /**
     * Facebook Reels:
     * Full-screen Reels viewer is characterized by fb_shorts and reels viewer containers.
     * Preserves newsfeed (`feed_recycler_view`, `android:id/list`) and Messenger.
     */
    private fun detectFacebookReels(root: AccessibilityNodeInfo): Boolean {
        // Preserve standard newsfeed
        if (root.findAccessibilityNodeInfosByViewId("android:id/list").isNotEmpty() ||
            root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/feed_recycler_view").isNotEmpty()
        ) {
            return false
        }

        for (viewId in FACEBOOK_REELS_VIEW_IDS) {
            val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
            if (nodes.isNotEmpty()) {
                return true
            }
        }

        // Inspect nodes for Reels identifiers in view ID
        var hasReelIndicator = false
        fun inspectNode(node: AccessibilityNodeInfo, depth: Int) {
            if (depth > 12 || hasReelIndicator) return
            val resId = node.viewIdResourceName ?: ""
            if (resId.contains("reel", ignoreCase = true) || resId.contains("fb_shorts", ignoreCase = true)) {
                hasReelIndicator = true
                return
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                inspectNode(child, depth + 1)
            }
        }

        inspectNode(root, 0)
        return hasReelIndicator
    }
}
