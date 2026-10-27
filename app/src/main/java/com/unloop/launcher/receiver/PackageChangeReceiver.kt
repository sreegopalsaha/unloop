package com.unloop.launcher.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.unloop.launcher.data.AppsRepository
import com.unloop.launcher.data.PreferencesManager
import com.unloop.launcher.util.AppCleanupManager
import com.unloop.launcher.util.AppIconCache
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
            val repository = AppsRepository(context, prefs)
            CoroutineScope(Dispatchers.IO).launch {
                repository.refreshApps()
            }
        }
    }
}
