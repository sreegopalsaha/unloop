package unloop.launcher

import android.content.res.Configuration
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.razorpay.PaymentData
import com.razorpay.PaymentResultWithDataListener
import unloop.launcher.subscription.payment.RazorpayCheckoutLauncher
import unloop.launcher.subscription.worker.SubscriptionSyncWorker
import unloop.launcher.ui.LauncherScreen
import unloop.launcher.ui.LauncherViewModel
import unloop.launcher.ui.screens.AboutScreen
import unloop.launcher.auth.model.AuthState
import unloop.launcher.ui.screens.FeedbackScreen
import unloop.launcher.ui.screens.AccountScreen
import unloop.launcher.ui.screens.AllAppsScreen
import unloop.launcher.ui.screens.AppCleanupScreen
import unloop.launcher.ui.screens.AppMenuDialog
import unloop.launcher.ui.screens.FocusSettingsScreen
import unloop.launcher.ui.screens.HiddenAppsScreen
import unloop.launcher.ui.screens.HomeScreen
import unloop.launcher.ui.screens.MindfulPromptDialog
import unloop.launcher.ui.screens.NotificationsScreen
import unloop.launcher.ui.screens.OnboardingScreen
import unloop.launcher.ui.screens.SettingsScreen
import unloop.launcher.ui.theme.UnloopTheme

class MainActivity : ComponentActivity(), PaymentResultWithDataListener {

    private val viewModel: LauncherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Preload Razorpay checkout resources and schedule background synchronization
        RazorpayCheckoutLauncher.preload(this)
        SubscriptionSyncWorker.schedulePeriodicSync(this)

        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            val isSystemDark = isSystemInDarkTheme()
            val isDark = when (themeMode) {
                "DARK" -> true
                "LIGHT" -> false
                else -> isSystemDark
            }
            val isDefaultLauncher by viewModel.isDefaultLauncher.collectAsState()
            LaunchedEffect(isDark, isDefaultLauncher) {
                if (isDefaultLauncher) {
                    viewModel.syncThemeWallpaper(isDark)
                } else {
                    viewModel.restoreOriginalWallpaperIfNeeded()
                }
            }

            UnloopTheme(darkTheme = isDark) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LauncherRoot(viewModel = viewModel, activity = this)
                }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val themeMode = viewModel.themeMode.value
        val isDefault = viewModel.isDefaultLauncher.value
        if (themeMode == "SYSTEM" && isDefault) {
            val isDark = (newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            viewModel.syncThemeWallpaper(isDark)
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshApps()
        viewModel.checkDefaultStatus()
        viewModel.checkPermissions()
    }

    override fun onPaymentSuccess(razorpayPaymentId: String?, paymentData: PaymentData?) {
        viewModel.handleRazorpaySuccess(razorpayPaymentId, paymentData)
    }

    override fun onPaymentError(errorCode: Int, response: String?, paymentData: PaymentData?) {
        viewModel.handleRazorpayError(errorCode, response, paymentData)
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
    val isUsageAccessGranted by viewModel.isUsageAccessGranted.collectAsState()
    val isUsageBannerDismissed by viewModel.isUsageBannerDismissed.collectAsState()
    val showSystemAppsCleanup by viewModel.showSystemAppsCleanup.collectAsState()
    val authState by viewModel.authState.collectAsState()

    // Subscription & Entitlement State
    val isEntitled by viewModel.isEntitled.collectAsState()
    val subscriptionPlans by viewModel.subscriptionPlans.collectAsState()
    val isPlansLoading by viewModel.isPlansLoading.collectAsState()
    val subscriptionPlansError by viewModel.subscriptionPlansError.collectAsState()
    val isSubscriptionProcessing by viewModel.isSubscriptionProcessing.collectAsState()
    val subscriptionErrorMessage by viewModel.subscriptionErrorMessage.collectAsState()
    val hasPendingPaymentVerification by viewModel.hasPendingPaymentVerification.collectAsState()
    val entitlementState by viewModel.entitlementState.collectAsState()

    Crossfade(targetState = currentScreen, label = "ScreenTransition") { screen ->
        when (screen) {
            LauncherScreen.ONBOARDING -> {
                OnboardingScreen(
                    isDefaultLauncher = isDefaultLauncher,
                    isIntroCompleted = viewModel.isIntroCompleted(),
                    initialPage = viewModel.getInitialOnboardingPage(),
                    authState = authState,
                    isEntitled = isEntitled,
                    plans = subscriptionPlans,
                    isPlansLoading = isPlansLoading,
                    plansError = subscriptionPlansError,
                    isSubscriptionProcessing = isSubscriptionProcessing,
                    subscriptionErrorMessage = subscriptionErrorMessage,
                    hasPendingPaymentVerification = hasPendingPaymentVerification,
                    onContinueWithGoogle = { viewModel.loginWithGoogle(activity) },
                    onDismissAuthError = { viewModel.dismissAuthError() },
                    onSubscribeMonthly = { viewModel.subscribeMonthly(activity) },
                    onPurchaseLifetime = { viewModel.purchaseLifetime(activity) },
                    onClearSubscriptionError = { viewModel.clearSubscriptionError() },
                    onRetrySubscriptionSync = { viewModel.retrySubscriptionSync() },
                    onRetryPaymentVerification = { viewModel.retryPaymentVerification() },
                    onRetryLoadPlans = { viewModel.refreshSubscriptionPlans() },
                    onOpenHomeSettings = { viewModel.openAndroidHomeSettings(activity) },
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
                    onRequestSetDefault = { viewModel.openSetDefaultLauncher(activity) },
                    onDoubleTap = { viewModel.lockDevice(activity) }
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
                    onOpenAppCleanup = { viewModel.navigateTo(LauncherScreen.APP_CLEANUP) },
                    onOpenNotifications = { viewModel.openNotifications(LauncherScreen.SETTINGS) },
                    onOpenScreenTime = { viewModel.openSystemDigitalWellbeing(activity) },
                    onChangeTheme = { viewModel.setThemeMode(it) },
                    authState = authState,
                    onOpenAccount = { viewModel.navigateTo(LauncherScreen.ACCOUNT) },
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

            LauncherScreen.ACCOUNT -> {
                AccountScreen(
                    authState = authState,
                    entitlementState = entitlementState,
                    plans = subscriptionPlans,
                    onLoginClick = { viewModel.loginWithGoogle(activity) },
                    onLogoutClick = { viewModel.logout() },
                    onCancelMonthlySubscription = { viewModel.cancelMonthlySubscription() },
                    onUpgradeClick = { viewModel.navigateTo(LauncherScreen.ONBOARDING) },
                    onDismissError = { viewModel.dismissAuthError() },
                    onBack = { viewModel.navigateTo(LauncherScreen.SETTINGS) }
                )
            }

            LauncherScreen.ABOUT -> {
                AboutScreen(
                    onBack = { viewModel.navigateTo(LauncherScreen.SETTINGS) },
                    onSendFeedbackClick = { viewModel.navigateTo(LauncherScreen.FEEDBACK) }
                )
            }

            LauncherScreen.FEEDBACK -> {
                val feedbackState by viewModel.feedbackSubmissionState.collectAsState()

                FeedbackScreen(
                    submissionState = feedbackState,
                    onSubmitFeedback = { text -> viewModel.submitFeedback(text) },
                    onResetState = { viewModel.resetFeedbackState() },
                    onBack = {
                        viewModel.resetFeedbackState()
                        viewModel.navigateTo(LauncherScreen.ABOUT)
                    }
                )
            }

            LauncherScreen.APP_CLEANUP -> {
                AppCleanupScreen(
                    installedApps = allInstalledApps,
                    isUsageAccessGranted = isUsageAccessGranted,
                    isUsageBannerDismissed = isUsageBannerDismissed,
                    showSystemApps = showSystemAppsCleanup,
                    onToggleShowSystemApps = { viewModel.setShowSystemAppsCleanup(it) },
                    onOpenUsageSettings = { viewModel.openUsageAccessSettings(activity) },
                    onDismissUsageBanner = { viewModel.dismissUsageBanner() },
                    onUninstallApp = { viewModel.uninstallApp(it) },
                    onOpenApp = { viewModel.handleAppClick(it) },
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
