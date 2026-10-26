package com.unloop.launcher.service

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONObject

data class FilteredNotificationItem(
    val key: String,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val timestamp: Long,
    val timestampFormatted: String = formatTimestamp(timestamp)
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("key", key)
            put("packageName", packageName)
            put("appName", appName)
            put("title", title)
            put("text", text)
            put("timestamp", timestamp)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): FilteredNotificationItem {
            val ts = json.optLong("timestamp", System.currentTimeMillis())
            return FilteredNotificationItem(
                key = json.optString("key", ""),
                packageName = json.optString("packageName", ""),
                appName = json.optString("appName", ""),
                title = json.optString("title", ""),
                text = json.optString("text", ""),
                timestamp = ts,
                timestampFormatted = formatTimestamp(ts)
            )
        }

        fun formatTimestamp(time: Long): String {
            return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(time))
        }
    }
}
