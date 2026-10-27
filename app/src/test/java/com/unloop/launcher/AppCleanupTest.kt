package com.unloop.launcher

import android.content.pm.ApplicationInfo
import androidx.compose.ui.graphics.ImageBitmap
import com.unloop.launcher.data.AppItem
import com.unloop.launcher.util.AppCleanupData
import com.unloop.launcher.util.AppCleanupManager
import com.unloop.launcher.util.AppIconCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy
import java.util.concurrent.TimeUnit

class AppCleanupTest {

    @Test
    fun testCategoryDisplayName_standardCategories() {
        assertEquals("Social Media", AppCleanupManager.getCategoryDisplayName(null, ApplicationInfo.CATEGORY_SOCIAL))
        assertEquals("Games", AppCleanupManager.getCategoryDisplayName(null, ApplicationInfo.CATEGORY_GAME))
        assertEquals("News", AppCleanupManager.getCategoryDisplayName(null, ApplicationInfo.CATEGORY_NEWS))
        assertEquals("Productivity", AppCleanupManager.getCategoryDisplayName(null, ApplicationInfo.CATEGORY_PRODUCTIVITY))
        assertEquals("Audio & Music", AppCleanupManager.getCategoryDisplayName(null, ApplicationInfo.CATEGORY_AUDIO))
        assertEquals("Video", AppCleanupManager.getCategoryDisplayName(null, ApplicationInfo.CATEGORY_VIDEO))
        assertEquals("Photos & Images", AppCleanupManager.getCategoryDisplayName(null, ApplicationInfo.CATEGORY_IMAGE))
        assertEquals("Maps & Navigation", AppCleanupManager.getCategoryDisplayName(null, ApplicationInfo.CATEGORY_MAPS))
        assertEquals("Accessibility", AppCleanupManager.getCategoryDisplayName(null, 8))
        assertEquals("Other Apps", AppCleanupManager.getCategoryDisplayName(null, ApplicationInfo.CATEGORY_UNDEFINED))
        assertEquals("Other Apps", AppCleanupManager.getCategoryDisplayName(null, -99))
    }

    @Test
    fun testGroupAppsByCategory_prioritizesSocialGamesNewsProductivity() {
        val apps = listOf(
            AppItem(packageName = "com.other.app", className = "Main", label = "Tool", category = ApplicationInfo.CATEGORY_UNDEFINED),
            AppItem(packageName = "com.news.app", className = "Main", label = "Daily News", category = ApplicationInfo.CATEGORY_NEWS),
            AppItem(packageName = "com.game.app", className = "Main", label = "Chess", category = ApplicationInfo.CATEGORY_GAME),
            AppItem(packageName = "com.prod.app", className = "Main", label = "Notes", category = ApplicationInfo.CATEGORY_PRODUCTIVITY),
            AppItem(packageName = "com.social.app", className = "Main", label = "Chat", category = ApplicationInfo.CATEGORY_SOCIAL),
            AppItem(packageName = "com.music.app", className = "Main", label = "Beats", category = ApplicationInfo.CATEGORY_AUDIO)
        )

        val unusedApps = listOf(
            apps[0]
        )

        val groups = AppCleanupManager.groupAppsByCategory(
            context = null,
            installedApps = apps,
            unusedApps = unusedApps,
            hasUsageAccess = true
        )

        assertEquals("Unused Apps", groups[0].title)
        assertTrue(groups[0].isUnusedCategory)
        assertEquals(1, groups[0].apps.size)

        val categoryTitles = groups.drop(1).map { it.title }
        assertEquals("Social Media", categoryTitles[0])
        assertEquals("Games", categoryTitles[1])
        assertEquals("News", categoryTitles[2])
        assertEquals("Productivity", categoryTitles[3])
        assertEquals("Audio & Music", categoryTitles[4])
        assertEquals("Other Apps", categoryTitles[5])
    }

    @Test
    fun testGroupAppsByCategory_emptyCategoriesAreOmitted() {
        val apps = listOf(
            AppItem(packageName = "com.game.app", className = "Main", label = "Chess", category = ApplicationInfo.CATEGORY_GAME)
        )

        val groups = AppCleanupManager.groupAppsByCategory(
            context = null,
            installedApps = apps,
            unusedApps = emptyList(),
            hasUsageAccess = false
        )

        assertEquals(2, groups.size)
        assertEquals("Unused Apps", groups[0].title)
        assertEquals("Games", groups[1].title)
        assertEquals(1, groups[1].apps.size)
    }

    @Test
    fun testAppItem_systemAppFlag() {
        val systemApp = AppItem(
            packageName = "com.android.settings",
            className = "Settings",
            label = "Settings",
            isSystemApp = true
        )
        val userApp = AppItem(
            packageName = "com.example.app",
            className = "Main",
            label = "Example",
            isSystemApp = false
        )

        assertTrue(systemApp.isSystemApp)
        assertFalse(userApp.isSystemApp)
    }

    @Test
    fun testFormatLastUsed_variousIntervals() {
        val now = 100_000_000_000L

        // Null, 0, or negative timestamps return null (unavailable)
        assertNull(AppCleanupManager.formatLastUsed(null, now))
        assertNull(AppCleanupManager.formatLastUsed(0L, now))
        assertNull(AppCleanupManager.formatLastUsed(-100L, now))

        // Future timestamp (clock skew)
        assertEquals("Last used recently", AppCleanupManager.formatLastUsed(now + 10_000L, now))

        // Same day (e.g. 2 hours ago)
        val twoHoursAgo = now - TimeUnit.HOURS.toMillis(2)
        assertEquals("Last used today", AppCleanupManager.formatLastUsed(twoHoursAgo, now))

        // Exactly 1 day ago
        val oneDayAgo = now - TimeUnit.DAYS.toMillis(1)
        assertEquals("Last used 1 day ago", AppCleanupManager.formatLastUsed(oneDayAgo, now))

        // 30 days ago (matching prompt requirement example)
        val thirtyDaysAgo = now - TimeUnit.DAYS.toMillis(30)
        assertEquals("Last used 30 days ago", AppCleanupManager.formatLastUsed(thirtyDaysAgo, now))

        // 90 days ago
        val ninetyDaysAgo = now - TimeUnit.DAYS.toMillis(90)
        assertEquals("Last used 90 days ago", AppCleanupManager.formatLastUsed(ninetyDaysAgo, now))
    }

    @Test
    fun testFilterUnusedApps_identifiesCorrectApps() {
        val now = 100_000_000_000L
        val app1 = AppItem(packageName = "com.app.active", className = "Main", label = "Active App")
        val app2 = AppItem(packageName = "com.app.unused", className = "Main", label = "Unused App")
        val app3 = AppItem(packageName = "com.app.brandnew", className = "Main", label = "Brand New App")
        val app4 = AppItem(packageName = "com.app.neverused", className = "Main", label = "Never Used App")

        val apps = listOf(app1, app2, app3, app4)

        val usageTimestamps = mapOf(
            "com.app.active" to (now - TimeUnit.DAYS.toMillis(2)),       // Used 2 days ago
            "com.app.unused" to (now - TimeUnit.DAYS.toMillis(25)),      // Used 25 days ago
            "com.app.brandnew" to 0L                                     // Installed 3 days ago, never used
        )

        val installTimestamps = mapOf(
            "com.app.active" to (now - TimeUnit.DAYS.toMillis(100)),
            "com.app.unused" to (now - TimeUnit.DAYS.toMillis(100)),
            "com.app.brandnew" to (now - TimeUnit.DAYS.toMillis(3)),     // Installed within 10 days
            "com.app.neverused" to (now - TimeUnit.DAYS.toMillis(50))    // Installed 50 days ago, no usage
        )

        val unused = AppCleanupManager.filterUnusedApps(
            installedApps = apps,
            usageTimestamps = usageTimestamps,
            installTimestamps = installTimestamps,
            now = now
        )

        val packageNames = unused.map { it.packageName }
        // app2 (used 25 days ago) and app4 (never used, installed 50 days ago) should be unused
        assertTrue(packageNames.contains("com.app.unused"))
        assertTrue(packageNames.contains("com.app.neverused"))
        // app1 (used 2 days ago) and app3 (installed 3 days ago) should NOT be flagged as unused
        assertFalse(packageNames.contains("com.app.active"))
        assertFalse(packageNames.contains("com.app.brandnew"))
    }

    @Test
    fun testSystemAppsFiltering_affectsCountsAndCategories() {
        val userApp1 = AppItem(packageName = "com.user.game", className = "Main", label = "Chess", category = ApplicationInfo.CATEGORY_GAME, isSystemApp = false)
        val userApp2 = AppItem(packageName = "com.user.social", className = "Main", label = "Chat", category = ApplicationInfo.CATEGORY_SOCIAL, isSystemApp = false)
        val systemApp1 = AppItem(packageName = "com.sys.game", className = "Main", label = "System Game", category = ApplicationInfo.CATEGORY_GAME, isSystemApp = true)
        val systemApp2 = AppItem(packageName = "com.sys.tools", className = "Main", label = "Sys Tool", category = ApplicationInfo.CATEGORY_UNDEFINED, isSystemApp = true)

        val allApps = listOf(userApp1, userApp2, systemApp1, systemApp2)

        // Filter: Show system apps OFF (default)
        val filteredOff = allApps.filterNot { it.isSystemApp }
        assertEquals(2, filteredOff.size)
        assertEquals(2, filteredOff.count { !it.isSystemApp })
        assertEquals(0, filteredOff.count { it.isSystemApp })

        val groupsOff = AppCleanupManager.groupAppsByCategory(
            context = null,
            installedApps = filteredOff,
            unusedApps = emptyList(),
            hasUsageAccess = false
        )
        val gameGroupOff = groupsOff.first { it.title == "Games" }
        assertEquals(1, gameGroupOff.apps.size) // Only userApp1
        // "Other Apps" should not be present because systemApp2 is filtered out
        assertFalse(groupsOff.any { it.title == "Other Apps" })

        // Filter: Show system apps ON
        val filteredOn = allApps
        assertEquals(4, filteredOn.size)
        assertEquals(2, filteredOn.count { !it.isSystemApp })
        assertEquals(2, filteredOn.count { it.isSystemApp })

        val groupsOn = AppCleanupManager.groupAppsByCategory(
            context = null,
            installedApps = filteredOn,
            unusedApps = emptyList(),
            hasUsageAccess = false
        )
        val gameGroupOn = groupsOn.first { it.title == "Games" }
        assertEquals(2, gameGroupOn.apps.size) // userApp1 and systemApp1
        // "Other Apps" is now present
        assertTrue(groupsOn.any { it.title == "Other Apps" })
    }

    @Test
    fun testProcessData_and_cachingInvalidation() {
        AppCleanupManager.clearCache()

        val app1 = AppItem(packageName = "com.test.app1", className = "Main", label = "App 1", category = ApplicationInfo.CATEGORY_GAME, isSystemApp = false)
        val app2 = AppItem(packageName = "com.test.app2", className = "Main", label = "App 2", category = ApplicationInfo.CATEGORY_SOCIAL, isSystemApp = true)
        val apps = listOf(app1, app2)

        val usageTimestamps = mapOf("com.test.app1" to 1000L)
        val installTimestamps = mapOf("com.test.app1" to 500L)

        // Initially no cached data
        assertNull(AppCleanupManager.getCachedCleanupData(apps, showSystemApps = false, isUsageAccessGranted = true))

        // Process data for showSystemApps = false
        val dataOff = AppCleanupManager.processData(
            context = null,
            installedApps = apps,
            showSystemApps = false,
            isUsageAccessGranted = true,
            usageTimestamps = usageTimestamps,
            installTimestamps = installTimestamps
        )
        assertEquals(2, dataOff.categoryGroups.size) // Unused Apps + Games
        assertEquals(1, dataOff.categoryGroups.first { it.title == "Games" }.apps.size)
        assertFalse(dataOff.categoryGroups.any { it.title == "Social Media" }) // system app excluded

        // Process data for showSystemApps = true
        val dataOn = AppCleanupManager.processData(
            context = null,
            installedApps = apps,
            showSystemApps = true,
            isUsageAccessGranted = true,
            usageTimestamps = usageTimestamps,
            installTimestamps = installTimestamps
        )
        assertEquals(3, dataOn.categoryGroups.size) // Unused Apps + Games + Social Media
        assertTrue(dataOn.categoryGroups.any { it.title == "Social Media" })

        // Invalidation tests
        assertFalse(AppCleanupManager.invalidateIfChanged(apps, isUsageAccessGranted = true))
        // Invalidate if apps change
        assertTrue(AppCleanupManager.invalidateIfChanged(listOf(app1), isUsageAccessGranted = true))
        // Invalidate if permission changes
        assertTrue(AppCleanupManager.invalidateIfChanged(listOf(app1), isUsageAccessGranted = false))
    }

    @Test
    fun testAppIconCache_operations() {
        AppIconCache.clear()

        val fakeBitmap = Proxy.newProxyInstance(
            ImageBitmap::class.java.classLoader,
            arrayOf(ImageBitmap::class.java)
        ) { _, _, _ -> null } as ImageBitmap

        assertNull(AppIconCache.get("com.example.test"))

        AppIconCache.put("com.example.test", fakeBitmap)
        assertNotNull(AppIconCache.get("com.example.test"))

        AppIconCache.remove("com.example.test")
        assertNull(AppIconCache.get("com.example.test"))

        AppIconCache.put("com.example.test2", fakeBitmap)
        AppIconCache.clear()
        assertNull(AppIconCache.get("com.example.test2"))
    }
}
