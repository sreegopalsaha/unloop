package unloop.launcher.util

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import unloop.launcher.data.AppItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class AppCategoryGroup(
    val key: String,
    val title: String,
    val apps: List<AppItem>,
    val isUnusedCategory: Boolean = false
)

data class AppCleanupData(
    val categoryGroups: List<AppCategoryGroup>,
    val usageTimestamps: Map<String, Long>
)

object AppCleanupManager {

    const val UNUSED_THRESHOLD_DAYS = 10L
    const val USAGE_QUERY_WINDOW_DAYS = 180L
    private const val USAGE_CACHE_TTL_MS = 60_000L

    private val installTimestampsCache = ConcurrentHashMap<String, Long>()
    private val categoryNameCache = ConcurrentHashMap<Int, String>()
    private var cachedUsageTimestamps: Map<String, Long>? = null
    private var lastUsageQueryTime: Long = 0L

    private val processedDataCache = ConcurrentHashMap<Boolean, AppCleanupData>()
    private var cachedAppsSignature: Long = 0L
    private var cachedUsageGranted: Boolean = false

    fun clearCache() {
        cachedUsageTimestamps = null
        lastUsageQueryTime = 0L
        installTimestampsCache.clear()
        categoryNameCache.clear()
        processedDataCache.clear()
        cachedAppsSignature = 0L
        cachedUsageGranted = false
    }

    private fun computeAppsSignature(apps: List<AppItem>): Long {
        var result = apps.size.toLong()
        for (app in apps) {
            result = 31L * result + app.packageName.hashCode().toLong()
        }
        return result
    }

    fun invalidateIfChanged(apps: List<AppItem>, isUsageAccessGranted: Boolean): Boolean {
        val sig = computeAppsSignature(apps)
        if (sig != cachedAppsSignature || isUsageAccessGranted != cachedUsageGranted) {
            processedDataCache.clear()
            cachedAppsSignature = sig
            cachedUsageGranted = isUsageAccessGranted
            return true
        }
        return false
    }

    fun getCachedCleanupData(
        installedApps: List<AppItem>,
        showSystemApps: Boolean,
        isUsageAccessGranted: Boolean
    ): AppCleanupData? {
        val changed = invalidateIfChanged(installedApps, isUsageAccessGranted)
        if (changed) {
            return null
        }
        return processedDataCache[showSystemApps]
    }

    @JvmOverloads
    fun getAppUsageTimestamps(
        context: Context,
        forceRefresh: Boolean = false
    ): Map<String, Long> {
        if (!PermissionUtils.hasUsageAccessPermission(context)) {
            return emptyMap()
        }

        val now = System.currentTimeMillis()
        val cached = cachedUsageTimestamps
        if (!forceRefresh && cached != null && (now - lastUsageQueryTime < USAGE_CACHE_TTL_MS)) {
            return cached
        }

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyMap()

        val startTime = now - (USAGE_QUERY_WINDOW_DAYS * 24L * 60L * 60L * 1000L)

        val resultMap = try {
            val map = usageStatsManager.queryAndAggregateUsageStats(startTime, now)
            if (!map.isNullOrEmpty()) {
                map.mapValues { it.value.lastTimeUsed }
            } else {
                val list = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_BEST, startTime, now) ?: emptyList()
                val fallbackMap = mutableMapOf<String, Long>()
                for (item in list) {
                    val existing = fallbackMap[item.packageName] ?: 0L
                    if (item.lastTimeUsed > existing) {
                        fallbackMap[item.packageName] = item.lastTimeUsed
                    }
                }
                fallbackMap
            }
        } catch (_: Exception) {
            emptyMap()
        }

        cachedUsageTimestamps = resultMap
        lastUsageQueryTime = now
        return resultMap
    }

    fun getAppInstallTimestamps(
        context: Context,
        installedApps: List<AppItem>
    ): Map<String, Long> {
        val pm = try {
            context.packageManager
        } catch (_: Throwable) {
            null
        } ?: return emptyMap()

        val missingApps = installedApps.filter { !installTimestampsCache.containsKey(it.packageName) }

        if (missingApps.isNotEmpty()) {
            try {
                val packages = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
                } else {
                    pm.getInstalledPackages(0)
                }
                if (!packages.isNullOrEmpty()) {
                    for (pkg in packages) {
                        if (pkg.firstInstallTime > 0L) {
                            installTimestampsCache[pkg.packageName] = pkg.firstInstallTime
                        }
                    }
                }
            } catch (_: Throwable) {
                // Fall back to per-app query below
            }

            for (app in missingApps) {
                if (!installTimestampsCache.containsKey(app.packageName)) {
                    val installTime = try {
                        pm.getPackageInfo(app.packageName, 0)?.firstInstallTime ?: 0L
                    } catch (_: Throwable) {
                        0L
                    }
                    if (installTime > 0L) {
                        installTimestampsCache[app.packageName] = installTime
                    }
                }
            }
        }

        val result = mutableMapOf<String, Long>()
        for (app in installedApps) {
            installTimestampsCache[app.packageName]?.let {
                result[app.packageName] = it
            }
        }
        return result
    }

    fun formatLastUsed(
        lastUsedTimestamp: Long?,
        now: Long = System.currentTimeMillis()
    ): String? {
        if (lastUsedTimestamp == null || lastUsedTimestamp <= 0L) {
            return null
        }
        if (lastUsedTimestamp > now) {
            return "Last used recently"
        }

        val diffMs = now - lastUsedTimestamp
        val days = TimeUnit.MILLISECONDS.toDays(diffMs)

        return when {
            days <= 0L -> "Last used today"
            days == 1L -> "Last used 1 day ago"
            else -> "Last used $days days ago"
        }
    }

    fun filterUnusedApps(
        installedApps: List<AppItem>,
        usageTimestamps: Map<String, Long>,
        installTimestamps: Map<String, Long> = emptyMap(),
        now: Long = System.currentTimeMillis(),
        unusedThresholdDays: Long = UNUSED_THRESHOLD_DAYS
    ): List<AppItem> {
        val thresholdMillis = now - (unusedThresholdDays * 24L * 60L * 60L * 1000L)

        return installedApps.filter { app ->
            val firstInstallTime = installTimestamps[app.packageName] ?: 0L
            if (firstInstallTime > 0L && firstInstallTime > thresholdMillis) {
                return@filter false
            }

            val lastUsed = usageTimestamps[app.packageName] ?: 0L
            lastUsed <= thresholdMillis
        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }

    fun getUnusedApps(
        context: Context,
        installedApps: List<AppItem>,
        usageTimestamps: Map<String, Long> = getAppUsageTimestamps(context)
    ): List<AppItem> {
        if (!PermissionUtils.hasUsageAccessPermission(context)) {
            return emptyList()
        }

        val installTimestamps = getAppInstallTimestamps(context, installedApps)

        return filterUnusedApps(
            installedApps = installedApps,
            usageTimestamps = usageTimestamps,
            installTimestamps = installTimestamps
        )
    }

    fun getCategoryDisplayName(context: Context?, category: Int): String {
        val cached = categoryNameCache[category]
        if (cached != null) return cached

        val name = when (category) {
            ApplicationInfo.CATEGORY_SOCIAL -> "Social Media"
            ApplicationInfo.CATEGORY_GAME -> "Games"
            ApplicationInfo.CATEGORY_NEWS -> "News"
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Productivity"
            ApplicationInfo.CATEGORY_AUDIO -> "Audio & Music"
            ApplicationInfo.CATEGORY_VIDEO -> "Video"
            ApplicationInfo.CATEGORY_IMAGE -> "Photos & Images"
            ApplicationInfo.CATEGORY_MAPS -> "Maps & Navigation"
            8 -> "Accessibility"
            ApplicationInfo.CATEGORY_UNDEFINED -> "Other Apps"
            else -> {
                val systemTitle = try {
                    if (context != null) {
                        ApplicationInfo.getCategoryTitle(context, category)?.toString()
                    } else {
                        null
                    }
                } catch (_: Exception) {
                    null
                }
                if (!systemTitle.isNullOrBlank()) systemTitle else "Other Apps"
            }
        }
        categoryNameCache[category] = name
        return name
    }

    private fun getCategoryPriority(category: Int): Int {
        return when (category) {
            ApplicationInfo.CATEGORY_SOCIAL -> 1
            ApplicationInfo.CATEGORY_GAME -> 2
            ApplicationInfo.CATEGORY_NEWS -> 3
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> 4
            ApplicationInfo.CATEGORY_AUDIO -> 5
            ApplicationInfo.CATEGORY_VIDEO -> 6
            ApplicationInfo.CATEGORY_IMAGE -> 7
            ApplicationInfo.CATEGORY_MAPS -> 8
            8 -> 9
            ApplicationInfo.CATEGORY_UNDEFINED -> 99
            else -> 10
        }
    }

    fun groupAppsByCategory(
        context: Context?,
        installedApps: List<AppItem>,
        unusedApps: List<AppItem>,
        hasUsageAccess: Boolean
    ): List<AppCategoryGroup> {
        val groups = mutableListOf<AppCategoryGroup>()

        groups.add(
            AppCategoryGroup(
                key = "unused_apps",
                title = "Unused Apps",
                apps = unusedApps,
                isUnusedCategory = true
            )
        )

        val categorizedMap = mutableMapOf<Int, MutableList<AppItem>>()

        for (app in installedApps) {
            val cat = if (app.category == ApplicationInfo.CATEGORY_UNDEFINED) {
                ApplicationInfo.CATEGORY_UNDEFINED
            } else {
                app.category
            }
            categorizedMap.getOrPut(cat) { mutableListOf() }.add(app)
        }

        val sortedCategories = categorizedMap.keys.sortedWith(
            compareBy<Int> { getCategoryPriority(it) }
                .thenBy { getCategoryDisplayName(context, it) }
        )

        for (cat in sortedCategories) {
            val apps = categorizedMap[cat]?.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label }) ?: emptyList()
            if (apps.isNotEmpty()) {
                val title = getCategoryDisplayName(context, cat)
                groups.add(
                    AppCategoryGroup(
                        key = "cat_$cat",
                        title = title,
                        apps = apps,
                        isUnusedCategory = false
                    )
                )
            }
        }

        return groups
    }

    fun processData(
        context: Context?,
        installedApps: List<AppItem>,
        showSystemApps: Boolean,
        isUsageAccessGranted: Boolean,
        usageTimestamps: Map<String, Long>,
        installTimestamps: Map<String, Long>
    ): AppCleanupData {
        val filteredApps = if (showSystemApps) {
            installedApps
        } else {
            installedApps.filterNot { it.isSystemApp }
        }

        val unusedApps = if (isUsageAccessGranted) {
            filterUnusedApps(
                installedApps = filteredApps,
                usageTimestamps = usageTimestamps,
                installTimestamps = installTimestamps
            )
        } else {
            emptyList()
        }

        val groups = groupAppsByCategory(
            context = context,
            installedApps = filteredApps,
            unusedApps = unusedApps,
            hasUsageAccess = isUsageAccessGranted
        )

        return AppCleanupData(
            categoryGroups = groups,
            usageTimestamps = usageTimestamps
        )
    }

    suspend fun loadCleanupData(
        context: Context,
        installedApps: List<AppItem>,
        showSystemApps: Boolean,
        isUsageAccessGranted: Boolean
    ): AppCleanupData = withContext(Dispatchers.IO) {
        invalidateIfChanged(installedApps, isUsageAccessGranted)

        val cached = processedDataCache[showSystemApps]
        if (cached != null) {
            return@withContext cached
        }

        val usageTimestamps = if (isUsageAccessGranted) {
            getAppUsageTimestamps(context)
        } else {
            emptyMap()
        }

        val installTimestamps = if (isUsageAccessGranted) {
            getAppInstallTimestamps(context, installedApps)
        } else {
            emptyMap()
        }

        val data = processData(
            context = context,
            installedApps = installedApps,
            showSystemApps = showSystemApps,
            isUsageAccessGranted = isUsageAccessGranted,
            usageTimestamps = usageTimestamps,
            installTimestamps = installTimestamps
        )
        processedDataCache[showSystemApps] = data
        data
    }

    suspend fun prewarmOtherToggleState(
        context: Context,
        installedApps: List<AppItem>,
        currentShowSystemApps: Boolean,
        isUsageAccessGranted: Boolean
    ) = withContext(Dispatchers.IO) {
        val otherToggle = !currentShowSystemApps
        if (processedDataCache.containsKey(otherToggle)) {
            return@withContext
        }

        val usageTimestamps = if (isUsageAccessGranted) {
            getAppUsageTimestamps(context)
        } else {
            emptyMap()
        }

        val installTimestamps = if (isUsageAccessGranted) {
            getAppInstallTimestamps(context, installedApps)
        } else {
            emptyMap()
        }

        val data = processData(
            context = context,
            installedApps = installedApps,
            showSystemApps = otherToggle,
            isUsageAccessGranted = isUsageAccessGranted,
            usageTimestamps = usageTimestamps,
            installTimestamps = installTimestamps
        )
        processedDataCache[otherToggle] = data
    }
}
