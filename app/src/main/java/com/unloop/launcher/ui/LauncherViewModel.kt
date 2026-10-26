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
import com.unloop.launcher.service.FilteredNotificationItem
import com.unloop.launcher.service.NotificationManagerService
import com.unloop.launcher.util.AppSearchEngine
import com.unloop.launcher.util.PermissionUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class LauncherScreen {
    ONBOARDING,
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

    private val _currentScreen = MutableStateFlow(determineInitialScreen())
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

    // Permissions tracking
    private val _isAccessibilityPermissionGranted = MutableStateFlow(PermissionUtils.isAccessibilityServiceEnabled(application))
    val isAccessibilityPermissionGranted: StateFlow<Boolean> = _isAccessibilityPermissionGranted.asStateFlow()

    private val _isNotificationPermissionGranted = MutableStateFlow(PermissionUtils.isNotificationListenerEnabled(application))
    val isNotificationPermissionGranted: StateFlow<Boolean> = _isNotificationPermissionGranted.asStateFlow()

    private val _isBatteryOptimizationIgnored = MutableStateFlow(PermissionUtils.isBatteryOptimizationIgnored(application))
    val isBatteryOptimizationIgnored: StateFlow<Boolean> = _isBatteryOptimizationIgnored.asStateFlow()

    private val _shouldShowBatteryOptimizationDialog = MutableStateFlow(false)
    val shouldShowBatteryOptimizationDialog: StateFlow<Boolean> = _shouldShowBatteryOptimizationDialog.asStateFlow()

    val filteredNotifications: StateFlow<List<FilteredNotificationItem>> = prefs.filteredNotificationsFlow

    private var pendingEnableShortVideoBlocker = false
    private var pendingEnableNotificationManager = false

    // Settings flows
    val shortVideoBlockerEnabled: StateFlow<Boolean> = combine(
        prefs.shortVideoBlockerFlow,
        _isAccessibilityPermissionGranted
    ) { enabled, granted ->
        enabled && granted
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val focusModeEnabled: StateFlow<Boolean> = prefs.focusModeFlow
    val focusApps: StateFlow<Set<String>> = prefs.focusAppsFlow
    val themeMode: StateFlow<String> = prefs.themeModeFlow

    val notificationManagerEnabled: StateFlow<Boolean> = combine(
        prefs.notificationManagerFlow,
        _isNotificationPermissionGranted
    ) { enabled, granted ->
        enabled && granted
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setNotificationManagerEnabled(enabled: Boolean) {
        prefs.setNotificationManagerEnabled(enabled)
    }

    fun requestToggleShortVideoBlocker(
        enabled: Boolean,
        onShowRationaleDialog: () -> Unit
    ) {
        if (enabled) {
            val granted = PermissionUtils.isAccessibilityServiceEnabled(getApplication())
            _isAccessibilityPermissionGranted.value = granted
            if (granted) {
                prefs.setShortVideoBlockerEnabled(true)
            } else {
                pendingEnableShortVideoBlocker = true
                onShowRationaleDialog()
            }
        } else {
            pendingEnableShortVideoBlocker = false
            prefs.setShortVideoBlockerEnabled(false)
        }
    }

    fun requestToggleNotificationManager(
        enabled: Boolean,
        onShowRationaleDialog: () -> Unit
    ) {
        val context = getApplication<Application>()
        if (enabled) {
            val granted = PermissionUtils.isNotificationListenerEnabled(context)
            _isNotificationPermissionGranted.value = granted
            if (granted) {
                prefs.setNotificationManagerEnabled(true)
                NotificationManagerService.requestRebindIfNecessary(context)
                NotificationManagerService.filterActiveNotificationsIfEnabled()
                val batteryIgnored = PermissionUtils.isBatteryOptimizationIgnored(context)
                _isBatteryOptimizationIgnored.value = batteryIgnored
                if (!batteryIgnored && !prefs.hasSeenBatteryOptimizationPrompt()) {
                    _shouldShowBatteryOptimizationDialog.value = true
                }
            } else {
                pendingEnableNotificationManager = true
                onShowRationaleDialog()
            }
        } else {
            pendingEnableNotificationManager = false
            prefs.setNotificationManagerEnabled(false)
            _shouldShowBatteryOptimizationDialog.value = false
        }
    }

    fun cancelPendingPermissions() {
        pendingEnableShortVideoBlocker = false
        pendingEnableNotificationManager = false
    }

    fun checkPermissions() {
        val context = getApplication<Application>()
        val accessibilityGranted = PermissionUtils.isAccessibilityServiceEnabled(context)
        val notificationGranted = PermissionUtils.isNotificationListenerEnabled(context)
        val batteryIgnored = PermissionUtils.isBatteryOptimizationIgnored(context)

        _isAccessibilityPermissionGranted.value = accessibilityGranted
        _isNotificationPermissionGranted.value = notificationGranted
        _isBatteryOptimizationIgnored.value = batteryIgnored

        // If permission was revoked outside the app, sync preference
        if (!accessibilityGranted && prefs.isShortVideoBlockerEnabled()) {
            prefs.setShortVideoBlockerEnabled(false)
        }
        if (!notificationGranted && prefs.isNotificationManagerEnabled()) {
            prefs.setNotificationManagerEnabled(false)
        }

        // Handle returning from system settings when user was prompted to enable a feature
        if (pendingEnableShortVideoBlocker) {
            if (accessibilityGranted) {
                prefs.setShortVideoBlockerEnabled(true)
            }
            pendingEnableShortVideoBlocker = false
        }

        if (pendingEnableNotificationManager) {
            if (notificationGranted) {
                prefs.setNotificationManagerEnabled(true)
                NotificationManagerService.requestRebindIfNecessary(context)
                NotificationManagerService.filterActiveNotificationsIfEnabled()
                if (!batteryIgnored && !prefs.hasSeenBatteryOptimizationPrompt()) {
                    _shouldShowBatteryOptimizationDialog.value = true
                }
            }
            pendingEnableNotificationManager = false
        }
    }

    fun openAccessibilitySettings(context: Context) {
        PermissionUtils.openAccessibilitySettings(context)
    }

    fun openNotificationListenerSettings(context: Context) {
        PermissionUtils.openNotificationListenerSettings(context)
    }

    fun requestIgnoreBatteryOptimization(context: Context) {
        PermissionUtils.requestIgnoreBatteryOptimization(context)
    }

    fun dismissBatteryOptimizationDialog() {
        prefs.setHasSeenBatteryOptimizationPrompt(true)
        _shouldShowBatteryOptimizationDialog.value = false
    }

    fun confirmBatteryOptimization(context: Context) {
        prefs.setHasSeenBatteryOptimizationPrompt(true)
        _shouldShowBatteryOptimizationDialog.value = false
        PermissionUtils.requestIgnoreBatteryOptimization(context)
    }

    fun getFilteredNotifications(): List<FilteredNotificationItem> {
        return prefs.getFilteredNotifications()
    }

    fun clearFilteredNotifications() {
        prefs.clearFilteredNotifications()
    }

    fun dismissFilteredNotification(item: FilteredNotificationItem) {
        prefs.removeFilteredNotification(item.key)
    }

    fun launchApp(packageName: String) {
        repository.launchApp(packageName)
    }

    init {
        refreshApps()
    }

    fun refreshApps() {
        viewModelScope.launch {
            repository.refreshApps()
            checkDefaultStatus()
            checkPermissions()
        }
    }

    private var notificationSourceScreen: LauncherScreen = LauncherScreen.HOME

    fun navigateTo(screen: LauncherScreen) {
        if (screen == LauncherScreen.ALL_APPS && _currentScreen.value != LauncherScreen.ALL_APPS) {
            _searchQuery.value = ""
        }
        _currentScreen.value = screen
    }

    fun openNotifications(fromScreen: LauncherScreen = _currentScreen.value) {
        notificationSourceScreen = fromScreen
        navigateTo(LauncherScreen.NOTIFICATIONS)
    }

    fun backFromNotifications() {
        navigateTo(notificationSourceScreen)
    }

    fun openPhone() {
        repository.openPhone()
    }

    fun openCamera() {
        repository.openCamera()
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

    fun setFocusApps(apps: Set<String>) {
        prefs.setFocusApps(apps)
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

    private fun determineInitialScreen(): LauncherScreen {
        val isDefault = checkIfDefaultLauncher()
        val isIntroDone = prefs.isIntroCompleted()
        // If user hasn't seen the intro, or hasn't finished setting default launcher, show onboarding
        return if (!isIntroDone || !isDefault) {
            LauncherScreen.ONBOARDING
        } else {
            LauncherScreen.HOME
        }
    }

    fun getInitialOnboardingPage(): Int {
        // If user already passed the intro explanations (screens 1 & 2) but launcher setup is pending,
        // take them directly to screen 3 (index 2) so they aren't forced to re-read the intro.
        return if (prefs.isIntroCompleted() && !checkIfDefaultLauncher()) 2 else 0
    }

    fun markIntroCompleted() {
        prefs.setIntroCompleted(true)
    }

    fun completeOnboarding() {
        prefs.setIntroCompleted(true)
        prefs.setOnboardingCompleted(true)
        _currentScreen.value = LauncherScreen.HOME
    }

    fun checkDefaultStatus() {
        val isDefault = checkIfDefaultLauncher()
        _isDefaultLauncher.value = isDefault
        if (isDefault) {
            prefs.setOnboardingCompleted(true)
            // If user was on onboarding and just completed default launcher setup, proceed to Home
            if (_currentScreen.value == LauncherScreen.ONBOARDING) {
                _currentScreen.value = LauncherScreen.HOME
            }
        }
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
