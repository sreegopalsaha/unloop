package com.unloop.launcher

import android.app.Notification
import com.unloop.launcher.service.FilteredNotificationItem
import com.unloop.launcher.service.NotificationManagerService
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationManagementTest {

    @Test
    fun testFilteredNotificationItem_serialization() {
        val timestamp = 1700000000000L
        val item = FilteredNotificationItem(
            key = "0|com.whatsapp|100|tag|1000",
            packageName = "com.whatsapp",
            appName = "WhatsApp",
            title = "Alice",
            text = "Hey, are you free?",
            timestamp = timestamp
        )

        val json = item.toJson()
        assertEquals("0|com.whatsapp|100|tag|1000", json.getString("key"))
        assertEquals("com.whatsapp", json.getString("packageName"))
        assertEquals("WhatsApp", json.getString("appName"))
        assertEquals("Alice", json.getString("title"))
        assertEquals("Hey, are you free?", json.getString("text"))
        assertEquals(timestamp, json.getLong("timestamp"))

        val restored = FilteredNotificationItem.fromJson(json)
        assertEquals(item.key, restored.key)
        assertEquals(item.packageName, restored.packageName)
        assertEquals(item.appName, restored.appName)
        assertEquals(item.title, restored.title)
        assertEquals(item.text, restored.text)
        assertEquals(item.timestamp, restored.timestamp)
        assertNotNull(restored.timestampFormatted)
    }

    @Test
    fun testNotificationList_addAndDeduplicate() {
        val list = mutableListOf<FilteredNotificationItem>()

        fun addNotification(item: FilteredNotificationItem) {
            list.removeAll { it.key == item.key }
            list.add(0, item)
        }

        val item1 = FilteredNotificationItem(
            key = "key1",
            packageName = "com.slack",
            appName = "Slack",
            title = "General",
            text = "New update",
            timestamp = 1000L
        )

        val item2 = FilteredNotificationItem(
            key = "key2",
            packageName = "com.google.android.gm",
            appName = "Gmail",
            title = "Receipt",
            text = "Your order has shipped",
            timestamp = 2000L
        )

        addNotification(item1)
        assertEquals(1, list.size)
        assertEquals("key1", list[0].key)

        addNotification(item2)
        assertEquals(2, list.size)
        assertEquals("key2", list[0].key) // Latest at index 0

        // Update item1 with new text
        val item1Updated = FilteredNotificationItem(
            key = "key1",
            packageName = "com.slack",
            appName = "Slack",
            title = "General",
            text = "New update: version 2",
            timestamp = 3000L
        )
        addNotification(item1Updated)

        // Should not duplicate, should move to top with updated content
        assertEquals(2, list.size)
        assertEquals("key1", list[0].key)
        assertEquals("New update: version 2", list[0].text)
        assertEquals("key2", list[1].key)
    }

    @Test
    fun testNotificationList_cappingAt100() {
        val list = mutableListOf<FilteredNotificationItem>()

        fun addNotification(item: FilteredNotificationItem) {
            list.removeAll { it.key == item.key }
            list.add(0, item)
            if (list.size > 100) {
                val capped = list.take(100)
                list.clear()
                list.addAll(capped)
            }
        }

        for (i in 1..110) {
            addNotification(
                FilteredNotificationItem(
                    key = "key_$i",
                    packageName = "com.app$i",
                    appName = "App $i",
                    title = "Title $i",
                    text = "Text $i",
                    timestamp = i * 1000L
                )
            )
        }

        assertEquals(100, list.size)
        // Most recent should be at the top
        assertEquals("key_110", list[0].key)
        // Oldest preserved should be key_11
        assertEquals("key_11", list[99].key)
    }

    @Test
    fun testNotificationList_dismissIndividual() {
        val list = mutableListOf(
            FilteredNotificationItem("k1", "pkg1", "App1", "T1", "M1", 1L),
            FilteredNotificationItem("k2", "pkg2", "App2", "T2", "M2", 2L),
            FilteredNotificationItem("k3", "pkg3", "App3", "T3", "M3", 3L)
        )

        val removed = list.removeAll { it.key == "k2" }
        assertTrue(removed)
        assertEquals(2, list.size)
        assertEquals("k1", list[0].key)
        assertEquals("k3", list[1].key)
    }

    @Test
    fun testNotificationList_clearAll() {
        val list = mutableListOf(
            FilteredNotificationItem("k1", "pkg1", "App1", "T1", "M1", 1L),
            FilteredNotificationItem("k2", "pkg2", "App2", "T2", "M2", 2L)
        )

        assertEquals(2, list.size)
        list.clear()
        assertTrue(list.isEmpty())
    }

    @Test
    fun testNotificationFilter_rulesValidation() {
        val unloopPackage = "com.unloop.launcher"

        fun shouldFilterNotification(
            pkg: String,
            isOngoing: Boolean,
            isClearable: Boolean,
            flags: Int,
            category: String?,
            isPhoneApp: Boolean = NotificationManagerService.isPhonePackage(pkg)
        ): Boolean {
            return NotificationManagerService.shouldFilterNotification(
                packageName = pkg,
                isOngoing = isOngoing,
                isClearable = isClearable,
                flags = flags,
                category = category,
                currentPackageName = unloopPackage,
                isPhoneApp = isPhoneApp
            )
        }

        // Regular clearable chat notification -> should filter
        assertTrue(
            shouldFilterNotification(
                pkg = "com.whatsapp",
                isOngoing = false,
                isClearable = true,
                flags = 0,
                category = Notification.CATEGORY_MESSAGE
            )
        )

        // Unloop own notification -> never filter
        assertFalse(
            shouldFilterNotification(
                pkg = unloopPackage,
                isOngoing = false,
                isClearable = true,
                flags = 0,
                category = null
            )
        )

        // Ongoing notification -> never filter
        assertFalse(
            shouldFilterNotification(
                pkg = "com.music.player",
                isOngoing = true,
                isClearable = true,
                flags = 0,
                category = null
            )
        )

        // Non-clearable notification -> never filter
        assertFalse(
            shouldFilterNotification(
                pkg = "com.android.systemui",
                isOngoing = false,
                isClearable = false,
                flags = 0,
                category = null
            )
        )

        // Foreground service -> never filter
        assertFalse(
            shouldFilterNotification(
                pkg = "com.download.manager",
                isOngoing = false,
                isClearable = true,
                flags = Notification.FLAG_FOREGROUND_SERVICE,
                category = null
            )
        )

        // Incoming call -> never filter
        assertFalse(
            shouldFilterNotification(
                pkg = "com.google.android.dialer",
                isOngoing = false,
                isClearable = true,
                flags = 0,
                category = Notification.CATEGORY_CALL
            )
        )

        // Missed call with CATEGORY_MISSED_CALL -> never filter
        assertFalse(
            shouldFilterNotification(
                pkg = "com.google.android.dialer",
                isOngoing = false,
                isClearable = true,
                flags = 0,
                category = Notification.CATEGORY_MISSED_CALL
            )
        )

        // Missed call without category from system Phone app -> never filter
        assertFalse(
            shouldFilterNotification(
                pkg = "com.google.android.dialer",
                isOngoing = false,
                isClearable = true,
                flags = 0,
                category = null
            )
        )

        // Call-related alert (e.g. voicemail) from system Phone app -> never filter
        assertFalse(
            shouldFilterNotification(
                pkg = "com.google.android.dialer",
                isOngoing = false,
                isClearable = true,
                flags = 0,
                category = "voicemail"
            )
        )

        // Notifications from other system phone packages (com.android.phone, com.android.dialer) -> never filter
        assertFalse(
            shouldFilterNotification(
                pkg = "com.android.phone",
                isOngoing = false,
                isClearable = true,
                flags = 0,
                category = null
            )
        )
        assertFalse(
            shouldFilterNotification(
                pkg = "com.android.dialer",
                isOngoing = false,
                isClearable = true,
                flags = 0,
                category = null
            )
        )

        // Dynamically identified default dialer -> never filter
        assertFalse(
            shouldFilterNotification(
                pkg = "com.custom.dialer",
                isOngoing = false,
                isClearable = true,
                flags = 0,
                category = null,
                isPhoneApp = true
            )
        )

        // System Alarm -> never filter
        assertFalse(
            shouldFilterNotification(
                pkg = "com.google.android.deskclock",
                isOngoing = false,
                isClearable = true,
                flags = 0,
                category = Notification.CATEGORY_ALARM
            )
        )

        // Active Stopwatch -> never filter
        assertFalse(
            shouldFilterNotification(
                pkg = "com.google.android.deskclock",
                isOngoing = false,
                isClearable = true,
                flags = 0,
                category = Notification.CATEGORY_STOPWATCH
            )
        )
    }

    @Test
    fun testToggleState_accuracyLogic() {
        fun computeEffectiveFilterState(prefEnabled: Boolean, permissionGranted: Boolean): Boolean {
            return prefEnabled && permissionGranted
        }

        // Neither enabled nor granted
        assertFalse(computeEffectiveFilterState(prefEnabled = false, permissionGranted = false))

        // Preference enabled but permission revoked/missing -> MUST BE FALSE
        assertFalse(computeEffectiveFilterState(prefEnabled = true, permissionGranted = false))

        // Permission granted but preference off -> FALSE
        assertFalse(computeEffectiveFilterState(prefEnabled = false, permissionGranted = true))

        // Both preference and permission active -> TRUE
        assertTrue(computeEffectiveFilterState(prefEnabled = true, permissionGranted = true))
    }

    @Test
    fun testBatteryOptimization_guidanceLogic() {
        fun shouldShowBatteryOptimizationDialog(
            filterEnabled: Boolean,
            batteryIgnored: Boolean,
            hasSeenPrompt: Boolean
        ): Boolean {
            return filterEnabled && !batteryIgnored && !hasSeenPrompt
        }

        // When battery optimization is already unrestricted -> No dialog
        assertFalse(
            shouldShowBatteryOptimizationDialog(
                filterEnabled = true,
                batteryIgnored = true,
                hasSeenPrompt = false
            )
        )

        // When filter is enabled, battery is optimized, and user has not seen prompt -> Show dialog
        assertTrue(
            shouldShowBatteryOptimizationDialog(
                filterEnabled = true,
                batteryIgnored = false,
                hasSeenPrompt = false
            )
        )

        // Once the user has seen or dismissed the prompt -> Never prompt repeatedly
        assertFalse(
            shouldShowBatteryOptimizationDialog(
                filterEnabled = true,
                batteryIgnored = false,
                hasSeenPrompt = true
            )
        )

        // If filter is disabled -> Do not prompt
        assertFalse(
            shouldShowBatteryOptimizationDialog(
                filterEnabled = false,
                batteryIgnored = false,
                hasSeenPrompt = false
            )
        )
    }
}
