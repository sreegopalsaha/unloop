package com.unloop.launcher.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.unloop.launcher.data.AppItem
import com.unloop.launcher.data.AppsRepository
import com.unloop.launcher.data.PreferencesManager
import com.unloop.launcher.util.AppSearchEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class LauncherScreen {
    HOME,
    ALL_APPS,
    SETTINGS,
    FOCUS_SETTINGS,
    HIDDEN_APPS,
    NOTIFICATIONS
}

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferencesManager.getInstance(application)
    private val repository = AppsRepository(application, prefs)

    private val _currentScreen = MutableStateFlow(LauncherScreen.HOME)
    val currentScreen: StateFlow<LauncherScreen> = _currentScreen.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedAppForMenu = MutableStateFlow<AppItem?>(null)
    val selectedAppForMenu: StateFlow<AppItem?> = _selectedAppForMenu.asStateFlow()

    private val _mindfulPromptApp = MutableStateFlow<AppItem?>(null)
    val mindfulPromptApp: StateFlow<AppItem?> = _mindfulPromptApp.asStateFlow()

    private val _isDefaultLauncher = MutableStateFlow(checkIfDefaultLauncher())
    val isDefaultLauncher: StateFlow<Boolean> = _isDefaultLauncher.asStateFlow()

    // Apps from repository
    val allInstalledApps: StateFlow<List<AppItem>> = repository.appsFlow

    // Visible apps (non-hidden)
    val visibleApps: StateFlow<List<AppItem>> = allInstalledApps.combine(prefs.hiddenAppsFlow) { apps, hidden ->
        apps.filterNot { hidden.contains(it.packageName) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Favorites
    val favoriteApps: StateFlow<List<AppItem>> = visibleApps.combine(prefs.favoritesFlow) { apps, favs ->
        apps.filter { favs.contains(it.packageName) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Filtered apps for Search
    val searchResults: StateFlow<List<AppItem>> = combine(visibleApps, _searchQuery) { apps, query ->
        if (query.isBlank()) {
            apps
        } else {
            AppSearchEngine.filterAndRank(apps, query)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Hidden apps list
    val hiddenAppsList: StateFlow<List<AppItem>> = allInstalledApps.combine(prefs.hiddenAppsFlow) { apps, hidden ->
        apps.filter { hidden.contains(it.packageName) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Settings flows
    val shortVideoBlockerEnabled: StateFlow<Boolean> = prefs.shortVideoBlockerFlow
    val focusModeEnabled: StateFlow<Boolean> = prefs.focusModeFlow
    val focusApps: StateFlow<Set<String>> = prefs.focusAppsFlow
    val themeMode: StateFlow<String> = prefs.themeModeFlow
    val notificationManagerEnabled = MutableStateFlow(prefs.isNotificationManagerEnabled())

    fun setNotificationManagerEnabled(enabled: Boolean) {
        prefs.setNotificationManagerEnabled(enabled)
        notificationManagerEnabled.value = enabled
    }

    fun getFilteredNotifications(): List<com.unloop.launcher.service.FilteredNotificationItem> {
        val jsonStr = prefs.getFilteredNotificationsJson()
        val list = mutableListOf<com.unloop.launcher.service.FilteredNotificationItem>()
        try {
            val jsonArray = org.json.JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                list.add(com.unloop.launcher.service.FilteredNotificationItem.fromJson(jsonArray.getJSONObject(i)))
            }
        } catch (_: Exception) {}
        return list
    }

    fun clearFilteredNotifications() {
        prefs.setFilteredNotificationsJson("[]")
    }

    init {
        refreshApps()
    }

    fun refreshApps() {
        viewModelScope.launch {
            repository.refreshApps()
            checkDefaultStatus()
        }
    }

    fun navigateTo(screen: LauncherScreen) {
        if (screen == LauncherScreen.ALL_APPS && _currentScreen.value != LauncherScreen.ALL_APPS) {
            _searchQuery.value = ""
        }
        _currentScreen.value = screen
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedAppForMenu(app: AppItem?) {
        _selectedAppForMenu.value = app
    }

    fun setMindfulPromptApp(app: AppItem?) {
        _mindfulPromptApp.value = app
    }

    fun handleAppClick(app: AppItem) {
        // If Focus Mode is enabled and this app is in focus restricted list, show mindful pause
        if (prefs.isFocusModeEnabled() && prefs.getFocusApps().contains(app.packageName)) {
            _mindfulPromptApp.value = app
        } else {
            repository.launchApp(app.packageName)
        }
    }

    fun launchAppConfirmed(app: AppItem) {
        _mindfulPromptApp.value = null
        repository.launchApp(app.packageName)
    }

    fun toggleFavorite(app: AppItem) {
        prefs.toggleFavorite(app.packageName)
        refreshApps()
    }

    fun setAppHidden(app: AppItem, hidden: Boolean) {
        prefs.setAppHidden(app.packageName, hidden)
        refreshApps()
    }

    fun toggleFocusRestriction(app: AppItem) {
        prefs.toggleFocusApp(app.packageName)
        refreshApps()
    }

    fun openAppDetails(app: AppItem) {
        repository.openAppDetails(app.packageName)
    }

    fun uninstallApp(app: AppItem) {
        repository.uninstallApp(app.packageName)
    }

    fun setShortVideoBlockerEnabled(enabled: Boolean) {
        prefs.setShortVideoBlockerEnabled(enabled)
    }

    fun setFocusModeEnabled(enabled: Boolean) {
        prefs.setFocusModeEnabled(enabled)
    }

    fun setFocusDurationMinutes(minutes: Int) {
        prefs.setFocusDurationMinutes(minutes)
    }

    fun getFocusDurationMinutes(): Int {
        return prefs.getFocusDurationMinutes()
    }

    fun setThemeMode(mode: String) {
        prefs.setThemeMode(mode)
    }

    fun checkDefaultStatus() {
        _isDefaultLauncher.value = checkIfDefaultLauncher()
    }

    private fun checkIfDefaultLauncher(): Boolean {
        val context = getApplication<Application>()
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolveInfo = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo?.activityInfo?.packageName == context.packageName
    }

    fun openSetDefaultLauncher(context: Context) {
        val intents = listOf(
            Intent(Settings.ACTION_HOME_SETTINGS),
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            },
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

    fun openSystemDigitalWellbeing(context: Context) {
        val intents = listOf(
            Intent().setClassName("com.google.android.apps.wellbeing", "com.google.android.apps.wellbeing.settings.TopLevelSettingsActivity"),
            Intent("com.google.android.apps.wellbeing.action.WELLBEING"),
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        )
        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            } catch (_: Exception) {}
        }
    }
}
