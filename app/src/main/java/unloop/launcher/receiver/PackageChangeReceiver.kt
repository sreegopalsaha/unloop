package unloop.launcher.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import unloop.launcher.data.AppsRepository
import unloop.launcher.data.PreferencesManager
import unloop.launcher.util.AppCleanupManager
import unloop.launcher.util.AppIconCache
import unloop.launcher.util.WallpaperThemeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PackageChangeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_PACKAGE_ADDED ||
            action == Intent.ACTION_PACKAGE_REMOVED ||
            action == Intent.ACTION_PACKAGE_REPLACED
        ) {
            AppCleanupManager.clearCache()
            AppIconCache.clear()
            val prefs = PreferencesManager.getInstance(context)
            val repository = AppsRepository.getInstance(context, prefs)
            val wallpaperThemeManager = WallpaperThemeManager.getInstance(context)
            CoroutineScope(Dispatchers.IO).launch {
                repository.refreshApps()
                if (prefs.wasThemeWallpaperApplied()) {
                    val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    val resolveInfo = context.packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
                    val isDefault = resolveInfo?.activityInfo?.packageName == context.packageName
                    if (!isDefault) {
                        wallpaperThemeManager.restoreOriginalWallpaper()
                    }
                }
            }
        }
    }
}
