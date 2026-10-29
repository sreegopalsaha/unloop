package unloop.launcher.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.provider.CallLog
import android.telecom.TelecomManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class AppsRepository(
    private val context: Context,
    private val preferencesManager: PreferencesManager
) {

    companion object {
        @Volatile
        private var instance: AppsRepository? = null

        fun getInstance(context: Context, preferencesManager: PreferencesManager): AppsRepository {
            return instance ?: synchronized(this) {
                instance ?: AppsRepository(
                    context.applicationContext,
                    preferencesManager
                ).also { instance = it }
            }
        }
    }

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
                    val appInfo = resolveInfo.activityInfo.applicationInfo
                    val category = appInfo?.category ?: ApplicationInfo.CATEGORY_UNDEFINED
                    val isSystem = if (appInfo != null) {
                        (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                        (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                    } else {
                        false
                    }
                    installedPackages.add(pkg)

                    AppItem(
                        packageName = pkg,
                        className = cls,
                        label = if (label.isBlank()) pkg else label,
                        isFavorite = favorites.contains(pkg),
                        isHidden = hidden.contains(pkg),
                        isFocusRestricted = focusApps.contains(pkg),
                        category = category,
                        isSystemApp = isSystem
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

            val deadFocus = focusApps.filterNot { installedPackages.contains(it) }.toSet()
            if (deadFocus.isNotEmpty()) {
                preferencesManager.setFocusApps(focusApps - deadFocus)
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

    fun isSystemApp(packageName: String): Boolean {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
            (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        } catch (_: Exception) {
            false
        }
    }

    fun uninstallApp(packageName: String): Boolean {
        return try {
            val uri = android.net.Uri.fromParts("package", packageName, null)
            val intent = Intent(Intent.ACTION_DELETE, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            android.util.Log.e("AppsRepository", "Failed to launch uninstall intent for $packageName", e)
            false
        }
    }

    fun openPhone(): Boolean {
        // 1. TelecomManager default dialer package (launches phone app's main/home screen)
        try {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            val defaultDialer = telecomManager?.defaultDialerPackage
            if (!defaultDialer.isNullOrBlank() && launchApp(defaultDialer)) {
                return true
            }
        } catch (_: Exception) {}

        // 2. Resolve package handling ACTION_DIAL and launch its main screen
        try {
            val dialIntent = Intent(Intent.ACTION_DIAL)
            val resolveInfo = context.packageManager.resolveActivity(dialIntent, PackageManager.MATCH_DEFAULT_ONLY)
            val pkg = resolveInfo?.activityInfo?.packageName
            if (!pkg.isNullOrBlank() && pkg != "android" && pkg != context.packageName && launchApp(pkg)) {
                return true
            }
        } catch (_: Exception) {}

        // 3. Query all packages handling ACTION_DIAL and launch the first valid one's main screen
        try {
            val dialIntent = Intent(Intent.ACTION_DIAL)
            val activities = context.packageManager.queryIntentActivities(dialIntent, 0)
            for (info in activities) {
                val dialerPkg = info.activityInfo?.packageName
                if (!dialerPkg.isNullOrBlank() && dialerPkg != "android" && dialerPkg != context.packageName && launchApp(dialerPkg)) {
                    return true
                }
            }
        } catch (_: Exception) {}

        // 4. Intent for Call Log / Recents screen
        try {
            val callLogIntent = Intent(Intent.ACTION_VIEW).apply {
                type = CallLog.Calls.CONTENT_TYPE
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (callLogIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(callLogIntent)
                return true
            }
        } catch (_: Exception) {}

        // 5. Fallback: Standard dialer intent (opens dial pad if no main screen could be launched)
        try {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                return true
            }
        } catch (_: Exception) {}

        // 6. Fallback: direct start ACTION_DIAL
        try {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return true
        } catch (_: Exception) {}

        return false
    }

    fun openCamera(): Boolean {
        // 1. Standard camera intents
        val cameraIntents = listOf(
            Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
            Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE),
            Intent("android.media.action.STILL_IMAGE_CAMERA_SECURE")
        )
        for (intent in cameraIntents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                    return true
                }
            } catch (_: Exception) {}
        }

        // 2. Fallback: Search installed apps for any camera package or label
        try {
            val cameraKeywords = listOf("camera", "cam")
            val cameraApp = _appsFlow.value.firstOrNull { app ->
                val pkgLower = app.packageName.lowercase()
                val labelLower = app.label.lowercase()
                cameraKeywords.any { pkgLower.contains(it) || labelLower.contains(it) }
            }
            if (cameraApp != null && launchApp(cameraApp.packageName)) {
                return true
            }
        } catch (_: Exception) {}

        // 3. Fallback: direct start STILL_IMAGE_CAMERA
        try {
            val intent = Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return true
        } catch (_: Exception) {}

        return false
    }
}
