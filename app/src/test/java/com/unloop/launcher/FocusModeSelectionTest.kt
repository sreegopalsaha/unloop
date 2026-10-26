package com.unloop.launcher

import com.unloop.launcher.data.AppItem
import com.unloop.launcher.util.AppSearchEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusModeSelectionTest {

    private val sampleApps = listOf(
        AppItem("com.google.android.youtube", "MainActivity", "YouTube"),
        AppItem("com.instagram.android", "MainActivity", "Instagram"),
        AppItem("com.twitter.android", "MainActivity", "Twitter"),
        AppItem("com.reddit.frontpage", "MainActivity", "Reddit"),
        AppItem("com.spotify.music", "MainActivity", "Spotify")
    )

    @Test
    fun testSelectAll_addsAllFilteredApps() {
        val initialFocusApps = setOf("com.spotify.music")
        val searchQuery = "you"
        val filteredApps = AppSearchEngine.filterAndRank(sampleApps, searchQuery)
        
        assertEquals(1, filteredApps.size)
        assertEquals("YouTube", filteredApps[0].label)

        val allFilteredSelected = filteredApps.isNotEmpty() && filteredApps.all { initialFocusApps.contains(it.packageName) }
        assertFalse(allFilteredSelected)

        val filteredPackageNames = filteredApps.map { it.packageName }.toSet()
        val updatedFocusApps = initialFocusApps + filteredPackageNames

        assertTrue(updatedFocusApps.contains("com.google.android.youtube"))
        assertTrue(updatedFocusApps.contains("com.spotify.music"))
        assertEquals(2, updatedFocusApps.size)
    }

    @Test
    fun testDeselectAll_removesOnlyFilteredApps() {
        val initialFocusApps = setOf("com.google.android.youtube", "com.instagram.android", "com.spotify.music")
        // Filter by "you" which matches YouTube only
        val filteredApps = AppSearchEngine.filterAndRank(sampleApps, "you")
        
        val allFilteredSelected = filteredApps.isNotEmpty() && filteredApps.all { initialFocusApps.contains(it.packageName) }
        assertTrue(allFilteredSelected)

        val filteredPackageNames = filteredApps.map { it.packageName }.toSet()
        val updatedFocusApps = initialFocusApps - filteredPackageNames

        assertFalse(updatedFocusApps.contains("com.google.android.youtube"))
        assertTrue(updatedFocusApps.contains("com.instagram.android"))
        assertTrue(updatedFocusApps.contains("com.spotify.music"))
        assertEquals(2, updatedFocusApps.size)
    }

    @Test
    fun testSelectAll_withoutSearch_selectsAllApps() {
        val initialFocusApps = emptySet<String>()
        val filteredApps = sampleApps

        val allFilteredSelected = filteredApps.isNotEmpty() && filteredApps.all { initialFocusApps.contains(it.packageName) }
        assertFalse(allFilteredSelected)

        val filteredPackageNames = filteredApps.map { it.packageName }.toSet()
        val updatedFocusApps = initialFocusApps + filteredPackageNames

        assertEquals(sampleApps.size, updatedFocusApps.size)
        sampleApps.forEach {
            assertTrue(updatedFocusApps.contains(it.packageName))
        }
    }

    @Test
    fun testDeselectAll_withoutSearch_clearsAllApps() {
        val initialFocusApps = sampleApps.map { it.packageName }.toSet()
        val filteredApps = sampleApps

        val allFilteredSelected = filteredApps.isNotEmpty() && filteredApps.all { initialFocusApps.contains(it.packageName) }
        assertTrue(allFilteredSelected)

        val filteredPackageNames = filteredApps.map { it.packageName }.toSet()
        val updatedFocusApps = initialFocusApps - filteredPackageNames

        assertTrue(updatedFocusApps.isEmpty())
    }
}
