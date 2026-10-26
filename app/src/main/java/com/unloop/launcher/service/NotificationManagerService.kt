package com.unloop.launcher.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.unloop.launcher.data.PreferencesManager
import org.json.JSONArray
import org.json.JSONObject

class NotificationManagerService : NotificationListenerService() {

    private lateinit var prefs: PreferencesManager

    override fun onCreate() {
        super.onCreate()
        prefs = PreferencesManager.getInstance(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        if (!::prefs.isInitialized) {
            prefs = PreferencesManager.getInstance(this)
        }

        if (!prefs.isNotificationManagerEnabled()) {
            return
        }

        val pkg = sbn.packageName ?: return
        // Do not intercept Unloop's own notifications or ongoing system alarms
        if (pkg == packageName || sbn.isOngoing) {
            return
        }

        val extras = sbn.notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        val pm = packageManager
        val appName = try {
            val appInfo = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            pkg
        }

        val item = FilteredNotificationItem(
            key = sbn.key,
            packageName = pkg,
            appName = appName,
            title = title,
            text = text,
            timestamp = sbn.postTime
        )

        // Cancel notification from Android system shade
        cancelNotification(sbn.key)

        // Store locally in preferences capped at 100 entries
        saveNotificationLocally(item)
    }

    @Synchronized
    private fun saveNotificationLocally(item: FilteredNotificationItem) {
        try {
            val currentJson = prefs.getFilteredNotificationsJson()
            val jsonArray = JSONArray(currentJson)
            val list = mutableListOf<JSONObject>()

            // Insert new item at the top
            list.add(item.toJson())

            for (i in 0 until jsonArray.length()) {
                if (list.size >= 100) break
                list.add(jsonArray.getJSONObject(i))
            }

            val updatedArray = JSONArray()
            for (obj in list) {
                updatedArray.put(obj)
            }
            prefs.setFilteredNotificationsJson(updatedArray.toString())
        } catch (_: Exception) {}
    }
}
