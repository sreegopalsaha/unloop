package com.unloop.launcher.util

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityManager
import androidx.core.app.NotificationManagerCompat
import com.unloop.launcher.service.ShortVideoBlockerAccessibilityService

object PermissionUtils {

    /**
     * Checks if the Accessibility Service is explicitly enabled by the user in Android settings.
     */
    fun isAccessibilityServiceEnabled(
        context: Context,
        serviceClass: Class<out AccessibilityService> = ShortVideoBlockerAccessibilityService::class.java
    ): Boolean {
        // 1. Check live service connection flag first
        if (ShortVideoBlockerAccessibilityService.isServiceConnected) {
            return true
        }

        val expectedComponentName = ComponentName(context, serviceClass)

        // 2. Query Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        try {
            val enabledServicesSetting = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            if (!enabledServicesSetting.isNullOrEmpty()) {
                val colonSplitter = TextUtils.SimpleStringSplitter(':')
                colonSplitter.setString(enabledServicesSetting)
                while (colonSplitter.hasNext()) {
                    val componentNameString = colonSplitter.next()
                    val enabledComponent = ComponentName.unflattenFromString(componentNameString)
                    if (enabledComponent != null &&
                        (enabledComponent == expectedComponentName ||
                            (enabledComponent.packageName == context.packageName &&
                                enabledComponent.className.endsWith(serviceClass.simpleName)))
                    ) {
                        return true
                    }
                }
            }
        } catch (_: Exception) {}

        // 3. Fallback: Query AccessibilityManager for enabled accessibility services
        try {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            val enabledServices = am?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            if (enabledServices != null) {
                for (service in enabledServices) {
                    val info = service.resolveInfo?.serviceInfo ?: continue
                    if (info.packageName == context.packageName &&
                        (info.name == serviceClass.name || info.name.endsWith(serviceClass.simpleName))
                    ) {
                        return true
                    }
                }
            }
        } catch (_: Exception) {}

        return false
    }

    /**
     * Checks if Notification Listener access has been granted to Unloop in Android system settings.
     */
    fun isNotificationListenerEnabled(context: Context): Boolean {
        try {
            val packageNames = NotificationManagerCompat.getEnabledListenerPackages(context)
            if (packageNames.contains(context.packageName)) {
                return true
            }
        } catch (_: Exception) {}

        // Fallback: Query Settings.Secure "enabled_notification_listeners"
        try {
            val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            if (!flat.isNullOrEmpty()) {
                val names = flat.split(":")
                for (name in names) {
                    val cn = ComponentName.unflattenFromString(name)
                    if (cn != null && cn.packageName == context.packageName) {
                        return true
                    }
                }
            }
        } catch (_: Exception) {}

        return false
    }

    /**
     * Guides the user to Android Accessibility Settings.
     */
    fun openAccessibilitySettings(context: Context) {
        val intents = listOf(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            } catch (_: Exception) {}
        }
    }

    /**
     * Guides the user to Android Notification Listener Access Settings.
     */
    fun openNotificationListenerSettings(context: Context) {
        val intents = listOf(
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            } catch (_: Exception) {}
        }
    }

    /**
     * Checks if Unloop is currently exempt from battery optimization.
     */
    fun isBatteryOptimizationIgnored(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Guides the user to grant unrestricted battery usage or request battery optimization exemption.
     */
    fun requestIgnoreBatteryOptimization(context: Context) {
        val directIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(directIntent)
            return
        } catch (_: Exception) {}

        openBatteryOptimizationSettings(context)
    }

    /**
     * Opens system battery optimization settings or App info details.
     */
    fun openBatteryOptimizationSettings(context: Context) {
        val settingsIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(settingsIntent)
            return
        } catch (_: Exception) {}

        try {
            val detailsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(detailsIntent)
        } catch (_: Exception) {}
    }
}
