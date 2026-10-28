package unloop.launcher.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.telecom.TelecomManager
import unloop.launcher.data.PreferencesManager
import unloop.launcher.util.PermissionUtils

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

    private fun isPhoneApp(pkg: String): Boolean {
        if (isPhonePackage(pkg)) {
            return true
        }

        // Dynamically check system default dialer package
        try {
            val telecomManager = getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            val defaultDialer = telecomManager?.defaultDialerPackage
            if (!defaultDialer.isNullOrBlank() && pkg == defaultDialer) {
                return true
            }
        } catch (_: Exception) {}

        // Dynamically check package handling ACTION_DIAL
        try {
            val dialIntent = Intent(Intent.ACTION_DIAL)
            val resolveInfo = packageManager.resolveActivity(dialIntent, PackageManager.MATCH_DEFAULT_ONLY)
            val dialerPkg = resolveInfo?.activityInfo?.packageName
            if (!dialerPkg.isNullOrBlank() && dialerPkg != "android" && dialerPkg != packageName && pkg == dialerPkg) {
                return true
            }
        } catch (_: Exception) {}

        return false
    }

    private fun processAndFilterNotification(sbn: StatusBarNotification) {
        val pkg = sbn.packageName ?: return
        val notification = sbn.notification ?: return

        if (!shouldFilterNotification(
                packageName = pkg,
                isOngoing = sbn.isOngoing,
                isClearable = sbn.isClearable,
                flags = notification.flags,
                category = notification.category,
                currentPackageName = packageName,
                isPhoneApp = isPhoneApp(pkg)
            )
        ) {
            return
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

        val PHONE_PACKAGES = setOf(
            "com.google.android.dialer",
            "com.android.dialer",
            "com.android.phone",
            "com.samsung.android.dialer",
            "com.samsung.android.incallui"
        )

        fun isPhonePackage(packageName: String): Boolean {
            return PHONE_PACKAGES.contains(packageName)
        }

        fun shouldFilterNotification(
            packageName: String,
            isOngoing: Boolean,
            isClearable: Boolean,
            flags: Int,
            category: String?,
            currentPackageName: String = "unloop.launcher",
            isPhoneApp: Boolean = isPhonePackage(packageName)
        ): Boolean {
            // 1. Do not intercept Unloop's own notifications or system Phone app
            if (packageName == currentPackageName || isPhoneApp) {
                return false
            }

            // 2. Do not intercept non-clearable, ongoing, or foreground tasks
            if (isOngoing || !isClearable) {
                return false
            }

            if ((flags and Notification.FLAG_ONGOING_EVENT) != 0 ||
                (flags and Notification.FLAG_NO_CLEAR) != 0 ||
                (flags and Notification.FLAG_FOREGROUND_SERVICE) != 0
            ) {
                return false
            }

            // 3. Preserve critical system alerts, phone calls, and alarms
            when (category) {
                Notification.CATEGORY_CALL,
                Notification.CATEGORY_MISSED_CALL,
                Notification.CATEGORY_ALARM,
                Notification.CATEGORY_STOPWATCH -> return false
            }

            return true
        }

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
