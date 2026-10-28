package unloop.launcher.util

import unloop.launcher.data.AppItem
import java.text.Normalizer
import java.util.Locale
import java.util.regex.Pattern

object AppSearchEngine {

    private val DIACRITICS_REGEX = Pattern.compile("\\p{InCombiningDiacriticalMarks}+")

    /**
     * Normalize a string by converting to lowercase, decomposing diacritics,
     * and replacing special localized characters.
     */
    fun normalize(input: String): String {
        if (input.isEmpty()) return ""
        val lower = input.lowercase(Locale.ROOT)
        val normalized = Normalizer.normalize(lower, Normalizer.Form.NFD)
        val stripped = DIACRITICS_REGEX.matcher(normalized).replaceAll("")

        // Character replacements for common languages
        val sb = StringBuilder(stripped.length)
        for (ch in stripped) {
            when (ch) {
                'ł' -> sb.append('l')
                'ø' -> sb.append('o')
                'ß' -> sb.append("ss")
                'æ' -> sb.append("ae")
                'œ' -> sb.append("oe")
                'đ' -> sb.append('d')
                else -> sb.append(ch)
            }
        }
        return sb.toString().trim()
    }

    /**
     * 4-Tier Match Filter and Ranker:
     * Tier 1: Prefix Match (starts with query)
     * Tier 2: Word Start / Acronym Match (a word inside label starts with query)
     * Tier 3: Substring Match (label contains query)
     * Tier 4: Fuzzy Match (characters appear sequentially)
     */
    fun filterAndRank(apps: List<AppItem>, query: String): List<AppItem> {
        val cleanQuery = normalize(query)
        if (cleanQuery.isEmpty()) {
            return apps
        }

        val tier1 = mutableListOf<AppItem>()
        val tier2 = mutableListOf<AppItem>()
        val tier3 = mutableListOf<AppItem>()
        val tier4 = mutableListOf<AppItem>()

        for (app in apps) {
            val normLabel = normalize(app.label)

            // Tier 1: Prefix Match
            if (normLabel.startsWith(cleanQuery)) {
                tier1.add(app)
                continue
            }

            // Tier 2: Word Start / Acronym Match
            val words = normLabel.split(Regex("[\\s_\\-\\.]+"))
            val wordMatch = words.any { it.startsWith(cleanQuery) }
            val acronym = words.mapNotNull { it.firstOrNull() }.joinToString("")
            if (wordMatch || (cleanQuery.length > 1 && acronym.startsWith(cleanQuery))) {
                tier2.add(app)
                continue
            }

            // Tier 3: Substring Match
            if (normLabel.contains(cleanQuery)) {
                tier3.add(app)
                continue
            }

            // Tier 4: Fuzzy Subsequence Match
            if (isSubsequence(cleanQuery, normLabel)) {
                tier4.add(app)
            }
        }

        return tier1 + tier2 + tier3 + tier4
    }

    private fun isSubsequence(query: String, target: String): Boolean {
        var qIdx = 0
        var tIdx = 0
        while (qIdx < query.length && tIdx < target.length) {
            if (query[qIdx] == target[tIdx]) {
                qIdx++
            }
            tIdx++
        }
        return qIdx == query.length
    }
}
