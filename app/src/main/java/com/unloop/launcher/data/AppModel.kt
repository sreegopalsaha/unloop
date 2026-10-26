package com.unloop.launcher.data

data class AppItem(
    val packageName: String,
    val className: String,
    val label: String,
    val isFavorite: Boolean = false,
    val isHidden: Boolean = false,
    val isFocusRestricted: Boolean = false
)
