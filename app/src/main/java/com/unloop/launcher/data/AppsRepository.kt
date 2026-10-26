package com.unloop.launcher.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class AppsRepository(
    private val context: Context,
    private val preferencesManager: PreferencesManager
) {

    private val _appsFlow = MutableStateFlow<List<AppItem>>(emptyList())
    val appsFlow: StateFlow<List<AppItem>> = _appsFlow.asStateFlow()

    suspend fun refreshApps() {
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }

            val resolveInfos: List<ResolveInfo> = try {
                pm.queryIntentActivities(intent, 0)
            } catch (e: Exception) {
                emptyList()
            }

            val favorites = preferencesManager.getFavorites()
            val hidden = preferencesManager.getHiddenApps()
            val focusApps = preferencesManager.getFocusApps()
            val currentPkg = context.packageName

            val installedPackages = mutableSetOf<String>()

            val items = resolveInfos
                .filter { it.activityInfo != null && it.activityInfo.packageName != currentPkg }
                .mapNotNull { resolveInfo ->
                    val pkg = resolveInfo.activityInfo.packageName
                    val cls = resolveInfo.activityInfo.name
                    val label = try {
                        resolveInfo.loadLabel(pm).toString().trim()
                    } catch (e: Exception) {
                        pkg
                    }
                    installedPackages.add(pkg)

                    AppItem(
                        packageName = pkg,
                        className = cls,
                        label = if (label.isBlank()) pkg else label,
                        isFavorite = favorites.contains(pkg),
                        isHidden = hidden.contains(pkg),
                        isFocusRestricted = focusApps.contains(pkg)
                    )
                }
                .distinctBy { it.packageName }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })

            // Clean up uninstalled packages from preferences
            val deadFavorites = favorites.filterNot { installedPackages.contains(it) }.toSet()
            if (deadFavorites.isNotEmpty()) {
                preferencesManager.setFavorites(favorites - deadFavorites)
            }
            val deadHidden = hidden.filterNot { installedPackages.contains(it) }.toSet()
            if (deadHidden.isNotEmpty()) {
                preferencesManager.setHiddenApps(hidden - deadHidden)
            }

            _appsFlow.value = items
        }
    }

    fun launchApp(packageName: String): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    fun openAppDetails(packageName: String) {
        try {
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }

    fun uninstallApp(packageName: String) {
        try {
            val intent = Intent(Intent.ACTION_DELETE).apply {
                data = android.net.Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }
}
