package com.unloop.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.unloop.launcher.ui.LauncherScreen
import com.unloop.launcher.ui.LauncherViewModel
import com.unloop.launcher.ui.screens.AboutScreen
import com.unloop.launcher.ui.screens.AllAppsScreen
import com.unloop.launcher.ui.screens.AppMenuDialog
import com.unloop.launcher.ui.screens.FocusSettingsScreen
import com.unloop.launcher.ui.screens.HiddenAppsScreen
import com.unloop.launcher.ui.screens.HomeScreen
import com.unloop.launcher.ui.screens.MindfulPromptDialog
import com.unloop.launcher.ui.screens.NotificationsScreen
import com.unloop.launcher.ui.screens.OnboardingScreen
import com.unloop.launcher.ui.screens.SettingsScreen
import com.unloop.launcher.ui.theme.UnloopTheme

class MainActivity : ComponentActivity() {

    private val viewModel: LauncherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            val isDark = when (themeMode) {
                "DARK" -> true
                "LIGHT" -> false
                else -> isSystemInDarkTheme()
            }

            UnloopTheme(darkTheme = isDark) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LauncherRoot(viewModel = viewModel, activity = this)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshApps()
        viewModel.checkDefaultStatus()
        viewModel.checkPermissions()
    }
}

@Composable
fun LauncherRoot(
    viewModel: LauncherViewModel,
    activity: ComponentActivity
) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val favoriteApps by viewModel.favoriteApps.collectAsState()
    val visibleApps by viewModel.visibleApps.collectAsState()
    val allInstalledApps by viewModel.allInstalledApps.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val hiddenAppsList by viewModel.hiddenAppsList.collectAsState()
    val selectedAppForMenu by viewModel.selectedAppForMenu.collectAsState()
    val mindfulPromptApp by viewModel.mindfulPromptApp.collectAsState()
    val isDefaultLauncher by viewModel.isDefaultLauncher.collectAsState()
    val shortVideoBlockerEnabled by viewModel.shortVideoBlockerEnabled.collectAsState()
    val focusModeEnabled by viewModel.focusModeEnabled.collectAsState()
    val focusApps by viewModel.focusApps.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val notificationManagerEnabled by viewModel.notificationManagerEnabled.collectAsState()
    val filteredNotifications by viewModel.filteredNotifications.collectAsState()
    val isBatteryOptimizationIgnored by viewModel.isBatteryOptimizationIgnored.collectAsState()
    val shouldShowBatteryOptimizationDialog by viewModel.shouldShowBatteryOptimizationDialog.collectAsState()

    Crossfade(targetState = currentScreen, label = "ScreenTransition") { screen ->
        when (screen) {
            LauncherScreen.ONBOARDING -> {
                OnboardingScreen(
                    isDefaultLauncher = isDefaultLauncher,
                    initialPage = viewModel.getInitialOnboardingPage(),
                    onSetDefaultLauncher = { viewModel.openSetDefaultLauncher(activity) },
                    onCompleteOnboarding = { viewModel.completeOnboarding() },
                    onIntroCompleted = { viewModel.markIntroCompleted() }
                )
            }

            LauncherScreen.HOME -> {
                HomeScreen(
                    favoriteApps = favoriteApps,
                    isDefaultLauncher = isDefaultLauncher,
                    onAppClick = { viewModel.handleAppClick(it) },
                    onAppLongClick = { viewModel.setSelectedAppForMenu(it) },
                    onOpenPhone = { viewModel.openPhone() },
                    onOpenNotifications = { viewModel.openNotifications(LauncherScreen.HOME) },
                    onOpenCamera = { viewModel.openCamera() },
                    onOpenSettings = { viewModel.navigateTo(LauncherScreen.SETTINGS) },
                    onOpenAllApps = { viewModel.navigateTo(LauncherScreen.ALL_APPS) },
                    onRequestSetDefault = { viewModel.openSetDefaultLauncher(activity) }
                )
            }

            LauncherScreen.ALL_APPS -> {
                AllAppsScreen(
                    searchQuery = searchQuery,
                    searchResults = searchResults,
                    onSearchQueryChange = { viewModel.updateSearchQuery(it) },
                    onAppClick = { viewModel.handleAppClick(it) },
                    onAppLongClick = { viewModel.setSelectedAppForMenu(it) },
                    onBackToHome = { viewModel.navigateTo(LauncherScreen.HOME) }
                )
            }

            LauncherScreen.SETTINGS -> {
                SettingsScreen(
                    isDefaultLauncher = isDefaultLauncher,
                    shortVideoBlockerEnabled = shortVideoBlockerEnabled,
                    focusModeEnabled = focusModeEnabled,
                    notificationManagerEnabled = notificationManagerEnabled,
                    themeMode = themeMode,
                    onSetDefaultLauncher = { viewModel.openSetDefaultLauncher(activity) },
                    onToggleShortVideoBlocker = { enabled, onShowRationale ->
                        viewModel.requestToggleShortVideoBlocker(enabled, onShowRationale)
                    },
                    onToggleFocusMode = { viewModel.setFocusModeEnabled(it) },
                    onToggleNotificationManager = { enabled, onShowRationale ->
                        viewModel.requestToggleNotificationManager(enabled, onShowRationale)
                    },
                    onOpenAccessibilitySettings = { viewModel.openAccessibilitySettings(activity) },
                    onOpenNotificationSettings = { viewModel.openNotificationListenerSettings(activity) },
                    onCancelPendingPermission = { viewModel.cancelPendingPermissions() },
                    onOpenFocusSettings = { viewModel.navigateTo(LauncherScreen.FOCUS_SETTINGS) },
                    onOpenHiddenApps = { viewModel.navigateTo(LauncherScreen.HIDDEN_APPS) },
                    onOpenNotifications = { viewModel.openNotifications(LauncherScreen.SETTINGS) },
                    onOpenScreenTime = { viewModel.openSystemDigitalWellbeing(activity) },
                    onChangeTheme = { viewModel.setThemeMode(it) },
                    onOpenAbout = { viewModel.navigateTo(LauncherScreen.ABOUT) },
                    onBackToHome = { viewModel.navigateTo(LauncherScreen.HOME) }
                )
            }

            LauncherScreen.FOCUS_SETTINGS -> {
                FocusSettingsScreen(
                    focusModeEnabled = focusModeEnabled,
                    focusApps = focusApps,
                    allApps = visibleApps,
                    onToggleFocusMode = { viewModel.setFocusModeEnabled(it) },
                    onToggleAppRestriction = { viewModel.toggleFocusRestriction(it) },
                    onSetFocusApps = { viewModel.setFocusApps(it) },
                    onBack = { viewModel.navigateTo(LauncherScreen.SETTINGS) }
                )
            }

            LauncherScreen.HIDDEN_APPS -> {
                HiddenAppsScreen(
                    hiddenApps = hiddenAppsList,
                    onOpenApp = { viewModel.handleAppClick(it) },
                    onUnhideApp = { viewModel.setAppHidden(it, false) },
                    onBack = { viewModel.navigateTo(LauncherScreen.SETTINGS) }
                )
            }

            LauncherScreen.NOTIFICATIONS -> {
                NotificationsScreen(
                    notificationsEnabled = notificationManagerEnabled,
                    notifications = filteredNotifications,
                    isBatteryOptimizationIgnored = isBatteryOptimizationIgnored,
                    showBatteryOptimizationDialog = shouldShowBatteryOptimizationDialog,
                    onToggleNotifications = { enabled, onShowRationale ->
                        viewModel.requestToggleNotificationManager(enabled, onShowRationale)
                    },
                    onOpenNotificationSettings = { viewModel.openNotificationListenerSettings(activity) },
                    onRequestBatteryOptimization = { viewModel.requestIgnoreBatteryOptimization(activity) },
                    onDismissBatteryOptimizationDialog = { viewModel.dismissBatteryOptimizationDialog() },
                    onConfirmBatteryOptimizationDialog = { viewModel.confirmBatteryOptimization(activity) },
                    onCancelPendingPermission = { viewModel.cancelPendingPermissions() },
                    onClearAll = { viewModel.clearFilteredNotifications() },
                    onDismissNotification = { viewModel.dismissFilteredNotification(it) },
                    onNotificationClick = { viewModel.launchApp(it.packageName) },
                    onBack = { viewModel.backFromNotifications() }
                )
            }

            LauncherScreen.ABOUT -> {
                AboutScreen(
                    onBack = { viewModel.navigateTo(LauncherScreen.SETTINGS) }
                )
            }
        }
    }

    // Contextual Action Dialog for Long Press
    selectedAppForMenu?.let { app ->
        AppMenuDialog(
            app = app,
            onDismiss = { viewModel.setSelectedAppForMenu(null) },
            onToggleFavorite = { viewModel.toggleFavorite(app) },
            onToggleHide = { viewModel.setAppHidden(app, !app.isHidden) },
            onToggleFocus = { viewModel.toggleFocusRestriction(app) },
            onAppInfo = { viewModel.openAppDetails(app) },
            onUninstall = { viewModel.uninstallApp(app) }
        )
    }

    // Mindful Pause Dialog for Focus Restricted Apps
    mindfulPromptApp?.let { app ->
        MindfulPromptDialog(
            app = app,
            onDismiss = { viewModel.setMindfulPromptApp(null) },
            onConfirmLaunch = { _ ->
                viewModel.launchAppConfirmed(app)
            }
        )
    }
}
