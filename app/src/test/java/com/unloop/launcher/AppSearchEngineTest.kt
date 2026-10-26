package com.unloop.launcher

import com.unloop.launcher.data.AppItem
import com.unloop.launcher.util.AppSearchEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSearchEngineTest {

    private val sampleApps = listOf(
        AppItem("com.google.android.youtube", "MainActivity", "YouTube"),
        AppItem("com.instagram.android", "MainActivity", "Instagram"),
        AppItem("com.whatsapp", "MainActivity", "WhatsApp"),
        AppItem("com.google.android.apps.maps", "MainActivity", "Google Maps"),
        AppItem("com.spotify.music", "MainActivity", "Spotify"),
        AppItem("com.example.polish", "MainActivity", "Łódź Guide")
    )

    @Test
    fun testNormalization_stripsDiacritics() {
        assertEquals("lodz guide", AppSearchEngine.normalize("Łódź Guide"))
        assertEquals("cafe", AppSearchEngine.normalize("Café"))
        assertEquals("uber", AppSearchEngine.normalize("Über"))
    }

    @Test
    fun testFilterAndRank_prefixMatchesFirst() {
        val results = AppSearchEngine.filterAndRank(sampleApps, "you")
        assertEquals(1, results.size)
        assertEquals("YouTube", results[0].label)
    }

    @Test
    fun testFilterAndRank_wordStartMatches() {
        // "maps" should match "Google Maps" via Tier 2 (word start)
        val results = AppSearchEngine.filterAndRank(sampleApps, "map")
        assertTrue(results.isNotEmpty())
        assertEquals("Google Maps", results[0].label)
    }

    @Test
    fun testFilterAndRank_caseAndDiacriticInsensitive() {
        val results = AppSearchEngine.filterAndRank(sampleApps, "lodz")
        assertEquals(1, results.size)
        assertEquals("Łódź Guide", results[0].label)
    }

    @Test
    fun testFilterAndRank_fuzzyMatch() {
        // "spt" matches "Spotify" via subsequence
        val results = AppSearchEngine.filterAndRank(sampleApps, "spt")
        assertTrue(results.any { it.label == "Spotify" })
    }

    @Test
    fun testFilterAndRank_blankQuery_returnsAll() {
        val resultsEmpty = AppSearchEngine.filterAndRank(sampleApps, "")
        assertEquals(sampleApps.size, resultsEmpty.size)

        val resultsWhitespace = AppSearchEngine.filterAndRank(sampleApps, "   ")
        assertEquals(sampleApps.size, resultsWhitespace.size)
    }

    @Test
    fun testFilterAndRank_noMatch_returnsEmpty() {
        val results = AppSearchEngine.filterAndRank(sampleApps, "zzzzzz")
        assertTrue(results.isEmpty())
    }
}
