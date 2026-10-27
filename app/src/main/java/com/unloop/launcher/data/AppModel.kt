package com.unloop.launcher.data

import android.content.pm.ApplicationInfo

data class AppItem(
    val packageName: String,
    val className: String,
    val label: String,
    val isFavorite: Boolean = false,
    val isHidden: Boolean = false,
    val isFocusRestricted: Boolean = false,
    val category: Int = ApplicationInfo.CATEGORY_UNDEFINED,
    val isSystemApp: Boolean = false
)

