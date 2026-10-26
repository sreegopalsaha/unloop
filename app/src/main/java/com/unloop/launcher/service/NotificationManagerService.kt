package com.unloop.launcher.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.unloop.launcher.data.PreferencesManager
import com.unloop.launcher.util.PermissionUtils

class NotificationManagerService : NotificationListenerService() {

    private lateinit var prefs: PreferencesManager

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = PreferencesManager.getInstance(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        ensurePrefs()
        if (prefs.isNotificationManagerEnabled()) {
            filterActiveNotifications()
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        if (instance == this) {
            instance = null
        }
        // Attempt rebind if permission is still granted
        requestRebindIfNecessary(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        ensurePrefs()
        if (!prefs.isNotificationManagerEnabled()) {
            return
        }

        processAndFilterNotification(sbn)
    }

    private fun ensurePrefs() {
        if (!::prefs.isInitialized) {
            prefs = PreferencesManager.getInstance(this)
        }
    }

    fun filterActiveNotifications() {
        try {
            val active = activeNotifications ?: return
            for (sbn in active) {
                processAndFilterNotification(sbn)
            }
        } catch (_: Exception) {}
    }

    private fun processAndFilterNotification(sbn: StatusBarNotification) {
        val pkg = sbn.packageName ?: return

        // 1. Do not intercept Unloop's own notifications
        if (pkg == packageName) {
            return
        }

        // 2. Do not intercept non-clearable, ongoing, or foreground tasks
        if (sbn.isOngoing || !sbn.isClearable) {
            return
        }

        val notification = sbn.notification ?: return
        val flags = notification.flags
        if ((flags and Notification.FLAG_ONGOING_EVENT) != 0 ||
            (flags and Notification.FLAG_NO_CLEAR) != 0 ||
            (flags and Notification.FLAG_FOREGROUND_SERVICE) != 0
        ) {
            return
        }

        // 3. Preserve critical system alerts, phone calls, and alarms
        when (notification.category) {
            Notification.CATEGORY_CALL,
            Notification.CATEGORY_ALARM,
            Notification.CATEGORY_STOPWATCH -> return
        }

        // 4. Extract notification text details robustly
        val extras = notification.extras
        var title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: ""
        if (title.isBlank()) {
            title = extras?.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString()?.trim() ?: ""
        }

        var text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim() ?: ""
        if (text.isBlank()) {
            text = extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim() ?: ""
        }
        if (text.isBlank()) {
            text = extras?.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim() ?: ""
        }
        if (text.isBlank()) {
            val textLines = extras?.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            if (!textLines.isNullOrEmpty()) {
                text = textLines.joinToString("\n") { it.toString().trim() }
            }
        }
        if (text.isBlank()) {
            text = notification.tickerText?.toString()?.trim() ?: ""
        }

        val pm = packageManager
        val appName = try {
            val appInfo = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            pkg
        }

        if (title.isBlank()) {
            title = appName
        }

        val postTime = if (sbn.postTime > 0) sbn.postTime else System.currentTimeMillis()
        val item = FilteredNotificationItem(
            key = sbn.key,
            packageName = pkg,
            appName = appName,
            title = title,
            text = text,
            timestamp = postTime
        )

        // Cancel notification from Android system shade so it doesn't distract the user
        try {
            cancelNotification(sbn.key)
        } catch (_: Exception) {
            try {
                @Suppress("DEPRECATION")
                cancelNotification(sbn.packageName, sbn.tag, sbn.id)
            } catch (_: Exception) {}
        }

        // Store locally in preferences
        prefs.addFilteredNotification(item)
    }

    companion object {
        @Volatile
        var instance: NotificationManagerService? = null
            private set

        val isServiceConnected: Boolean
            get() = instance != null

        fun requestRebindIfNecessary(context: Context) {
            if (instance == null && PermissionUtils.isNotificationListenerEnabled(context)) {
                try {
                    requestRebind(ComponentName(context, NotificationManagerService::class.java))
                } catch (_: Exception) {}
            }
        }

        fun filterActiveNotificationsIfEnabled() {
            instance?.let { service ->
                service.ensurePrefs()
                if (service.prefs.isNotificationManagerEnabled()) {
                    service.filterActiveNotifications()
                }
            }
        }
    }
}
