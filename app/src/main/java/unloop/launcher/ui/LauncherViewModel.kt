package unloop.launcher.ui

import android.app.Activity
import android.app.Application
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.razorpay.Checkout
import com.razorpay.PaymentData
import unloop.launcher.auth.AuthRepository
import unloop.launcher.auth.SessionValidationResult
import unloop.launcher.auth.GoogleAuthCancellationException
import unloop.launcher.auth.model.AuthSession
import unloop.launcher.auth.model.AuthState
import unloop.launcher.data.AppItem
import unloop.launcher.data.AppsRepository
import unloop.launcher.data.PreferencesManager
import unloop.launcher.service.FilteredNotificationItem
import unloop.launcher.service.NotificationManagerService
import unloop.launcher.service.ShortVideoBlockerAccessibilityService
import unloop.launcher.subscription.SubscriptionRepository
import unloop.launcher.subscription.model.AlreadyEntitledException
import unloop.launcher.subscription.model.CheckoutIntent
import unloop.launcher.subscription.model.EntitlementInfo
import unloop.launcher.subscription.model.EntitlementState
import unloop.launcher.subscription.model.PendingPaymentVerification
import unloop.launcher.subscription.model.SubscriptionPlan
import unloop.launcher.subscription.payment.RazorpayCheckoutLauncher
import unloop.launcher.util.AppSearchEngine
import unloop.launcher.util.PermissionUtils
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
    NOTIFICATIONS,
    ABOUT,
    APP_CLEANUP,
    ACCOUNT
}

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferencesManager.getInstance(application)
    private val repository = AppsRepository(application, prefs)
    private val authRepository = AuthRepository(prefs)
    private val subscriptionRepository = SubscriptionRepository(application, prefs)

    val authSession: StateFlow<AuthSession?> = authRepository.authSessionFlow
    private val _authState = MutableStateFlow<AuthState>(
        if (authRepository.hasValidSession()) {
            prefs.getAuthSession()?.let { AuthState.Authenticated(it) } ?: AuthState.Idle
        } else {
            AuthState.Idle
        }
    )
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    // Subscription & Entitlement State
    val isEntitled: StateFlow<Boolean> = subscriptionRepository.isEntitled
    val entitlementState: StateFlow<EntitlementState> = subscriptionRepository.entitlementState
    val subscriptionPlans: StateFlow<List<SubscriptionPlan>> = subscriptionRepository.cachedPlans

    private val _isSubscriptionProcessing = MutableStateFlow(false)
    val isSubscriptionProcessing: StateFlow<Boolean> = _isSubscriptionProcessing.asStateFlow()

    private val _subscriptionErrorMessage = MutableStateFlow<String?>(null)
    val subscriptionErrorMessage: StateFlow<String?> = _subscriptionErrorMessage.asStateFlow()

    private var pendingMonthlyIntent: CheckoutIntent.Monthly? = null
    private var pendingLifetimeIntent: CheckoutIntent.Lifetime? = null
    private var lastFailedPayment: PendingPaymentVerification? = null
    private val _hasPendingPaymentVerification = MutableStateFlow(false)
    val hasPendingPaymentVerification: StateFlow<Boolean> = _hasPendingPaymentVerification.asStateFlow()

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

    private val _isUsageAccessGranted = MutableStateFlow(PermissionUtils.hasUsageAccessPermission(application))
    val isUsageAccessGranted: StateFlow<Boolean> = _isUsageAccessGranted.asStateFlow()

    private val _isUsageBannerDismissed = MutableStateFlow(prefs.hasDismissedUsageAccessPrompt())
    val isUsageBannerDismissed: StateFlow<Boolean> = _isUsageBannerDismissed.asStateFlow()

    fun dismissUsageBanner() {
        prefs.setHasDismissedUsageAccessPrompt(true)
        _isUsageBannerDismissed.value = true
    }

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
    val showSystemAppsCleanup: StateFlow<Boolean> = prefs.showSystemAppsCleanupFlow

    fun setShowSystemAppsCleanup(show: Boolean) {
        prefs.setShowSystemAppsCleanup(show)
    }

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
        val usageGranted = PermissionUtils.hasUsageAccessPermission(context)

        _isAccessibilityPermissionGranted.value = accessibilityGranted
        _isNotificationPermissionGranted.value = notificationGranted
        _isBatteryOptimizationIgnored.value = batteryIgnored
        _isUsageAccessGranted.value = usageGranted

        if (!accessibilityGranted && prefs.isShortVideoBlockerEnabled()) {
            prefs.setShortVideoBlockerEnabled(false)
        }
        if (!notificationGranted && prefs.isNotificationManagerEnabled()) {
            prefs.setNotificationManagerEnabled(false)
        }

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

    fun openUsageAccessSettings(context: Context) {
        PermissionUtils.openUsageAccessSettings(context)
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

    fun lockDevice(context: Context) {
        if (!PermissionUtils.isDoubleTapToLockEnabledInSystem(context)) {
            return
        }

        if (ShortVideoBlockerAccessibilityService.lockScreen()) {
            return
        }

        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        try {
            if (dpm != null) {
                dpm.lockNow()
                return
            }
        } catch (_: Exception) {}

        if (PermissionUtils.isAccessibilityServiceEnabled(context)) {
            Toast.makeText(
                context,
                "Connecting to accessibility service, please try again",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            Toast.makeText(
                context,
                "Enable Unloop in Accessibility settings to allow locking the screen",
                Toast.LENGTH_SHORT
            ).show()
            PermissionUtils.openAccessibilitySettings(context)
        }
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
        viewModelScope.launch {
            subscriptionRepository.refreshPlans()
        }
    }

    fun refreshApps() {
        viewModelScope.launch {
            repository.refreshApps()
            checkDefaultStatus()
            checkPermissions()
            checkSessionValidation()
        }
    }

    private var notificationSourceScreen: LauncherScreen = LauncherScreen.HOME

    fun navigateTo(screen: LauncherScreen) {
        if ((!authRepository.hasValidSession() || !subscriptionRepository.evaluateLocalEntitlement()) && screen != LauncherScreen.ONBOARDING) {
            _currentScreen.value = LauncherScreen.ONBOARDING
            return
        }
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
        if (!authRepository.hasValidSession()) {
            return LauncherScreen.ONBOARDING
        }
        val isEntitled = subscriptionRepository.evaluateLocalEntitlement()
        if (!isEntitled) {
            return LauncherScreen.ONBOARDING
        }
        // If onboarding has not been completed, stay in onboarding until the user completes the flow
        if (!prefs.isOnboardingCompleted()) {
            return LauncherScreen.ONBOARDING
        }
        return LauncherScreen.HOME
    }

    fun getInitialOnboardingPage(): Int {
        if (!authRepository.hasValidSession()) {
            return 0
        }
        if (!prefs.isIntroCompleted()) {
            return 1
        }
        val isEntitled = subscriptionRepository.evaluateLocalEntitlement()
        if (!isEntitled) {
            return 2 // Subscription Onboarding Screen
        }
        return 3 // Default Launcher Setup
    }

    fun isIntroCompleted(): Boolean = prefs.isIntroCompleted()

    fun markIntroCompleted() {
        prefs.setIntroCompleted(true)
    }

    fun completeOnboarding() {
        if (!authRepository.hasValidSession() || !subscriptionRepository.evaluateLocalEntitlement()) {
            _currentScreen.value = LauncherScreen.ONBOARDING
            return
        }
        prefs.setIntroCompleted(true)
        prefs.setOnboardingCompleted(true)
        _currentScreen.value = LauncherScreen.HOME
    }

    fun checkDefaultStatus() {
        val isDefault = checkIfDefaultLauncher()
        _isDefaultLauncher.value = isDefault
        if (isDefault && authRepository.hasValidSession() && subscriptionRepository.evaluateLocalEntitlement()) {
            if (prefs.isOnboardingCompleted() && _currentScreen.value == LauncherScreen.ONBOARDING) {
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
        if (!authRepository.hasValidSession()) {
            _currentScreen.value = LauncherScreen.ONBOARDING
            return
        }
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

    init {
        viewModelScope.launch {
            authSession.collect { session ->
                if (session != null) {
                    _authState.value = AuthState.Authenticated(session)
                    subscriptionRepository.onUserLoggedIn()
                } else {
                    if (_authState.value is AuthState.Authenticated) {
                        _authState.value = AuthState.Idle
                    }
                    subscriptionRepository.onUserLoggedOut()
                    if (_currentScreen.value != LauncherScreen.ONBOARDING) {
                        _currentScreen.value = LauncherScreen.ONBOARDING
                    }
                }
            }
        }
        checkSessionValidation()
    }

    fun checkSessionValidation() {
        if (!authRepository.hasValidSession()) return
        viewModelScope.launch {
            val result = authRepository.validateSessionIfNeeded()
            if (result is SessionValidationResult.Invalid) {
                _authState.value = AuthState.Error(result.reason)
                _currentScreen.value = LauncherScreen.ONBOARDING
            } else {
                val syncResult = subscriptionRepository.synchronizeEntitlement(forceServerCheck = false)
                if (syncResult.isSuccess && syncResult.getOrNull()?.isEntitled == true) {
                    if (prefs.isOnboardingCompleted() && _currentScreen.value == LauncherScreen.ONBOARDING) {
                        _currentScreen.value = LauncherScreen.HOME
                    }
                }
            }
        }
    }

    fun loginWithGoogle(activityContext: Context) {
        if (_authState.value is AuthState.Loading) return
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            val result = authRepository.loginWithGoogle(activityContext)
            result.fold(
                onSuccess = { session ->
                    _authState.value = AuthState.Authenticated(session)
                    subscriptionRepository.onUserLoggedIn()
                },
                onFailure = { error ->
                    if (error is GoogleAuthCancellationException) {
                        val currentSession = prefs.getAuthSession()
                        _authState.value = if (currentSession != null && authRepository.hasValidSession()) {
                            AuthState.Authenticated(currentSession)
                        } else {
                            AuthState.Idle
                        }
                    } else {
                        _authState.value = AuthState.Error(
                            error.message ?: "Authentication failed. Please check your connection and try again."
                        )
                    }
                }
            )
        }
    }

    fun logout() {
        authRepository.logout()
        subscriptionRepository.onUserLoggedOut()
        prefs.setIntroCompleted(false)
        prefs.setOnboardingCompleted(false)
        _authState.value = AuthState.Idle
        _currentScreen.value = LauncherScreen.ONBOARDING
    }

    fun dismissAuthError() {
        val currentSession = prefs.getAuthSession()
        _authState.value = if (currentSession != null && authRepository.hasValidSession()) {
            AuthState.Authenticated(currentSession)
        } else {
            AuthState.Idle
        }
    }

    // --- Subscription & Payment Actions ---

    fun subscribeMonthly(activity: Activity) {
        if (_isSubscriptionProcessing.value) return
        _isSubscriptionProcessing.value = true
        _subscriptionErrorMessage.value = null

        viewModelScope.launch {
            val result = subscriptionRepository.initiateMonthlySubscription()
            result.fold(
                onSuccess = { monthlyIntent ->
                    pendingMonthlyIntent = monthlyIntent
                    pendingLifetimeIntent = null
                    val email = prefs.getAuthSession()?.user?.email ?: ""
                    val launched = RazorpayCheckoutLauncher.openSubscriptionCheckout(activity, monthlyIntent, email)
                    if (!launched) {
                        _isSubscriptionProcessing.value = false
                        _subscriptionErrorMessage.value = "Unable to start Razorpay checkout. Please check connection."
                    }
                },
                onFailure = { error ->
                    _isSubscriptionProcessing.value = false
                    if (error is AlreadyEntitledException ||
                        error.message?.contains("already have Lifetime Premium", ignoreCase = true) == true ||
                        error.message?.contains("already own Lifetime Premium", ignoreCase = true) == true) {
                        _subscriptionErrorMessage.value = null
                        unlockExistingEntitlement()
                    } else {
                        _subscriptionErrorMessage.value = error.message ?: "Failed to initiate monthly subscription"
                    }
                }
            )
        }
    }

    fun purchaseLifetime(activity: Activity) {
        if (_isSubscriptionProcessing.value) return
        _isSubscriptionProcessing.value = true
        _subscriptionErrorMessage.value = null

        viewModelScope.launch {
            val result = subscriptionRepository.initiateLifetimePurchase()
            result.fold(
                onSuccess = { lifetimeIntent ->
                    pendingLifetimeIntent = lifetimeIntent
                    pendingMonthlyIntent = null
                    val email = prefs.getAuthSession()?.user?.email ?: ""
                    val launched = RazorpayCheckoutLauncher.openLifetimeCheckout(activity, lifetimeIntent, email)
                    if (!launched) {
                        _isSubscriptionProcessing.value = false
                        _subscriptionErrorMessage.value = "Unable to start Razorpay checkout. Please check connection."
                    }
                },
                onFailure = { error ->
                    _isSubscriptionProcessing.value = false
                    if (error is AlreadyEntitledException ||
                        error.message?.contains("already own Lifetime Premium", ignoreCase = true) == true ||
                        error.message?.contains("already have Lifetime Premium", ignoreCase = true) == true) {
                        _subscriptionErrorMessage.value = null
                        unlockExistingEntitlement()
                    } else {
                        _subscriptionErrorMessage.value = error.message ?: "Failed to initiate lifetime purchase"
                    }
                }
            )
        }
    }

    fun unlockExistingEntitlement() {
        _isSubscriptionProcessing.value = true
        viewModelScope.launch {
            val syncResult = subscriptionRepository.synchronizeEntitlement(forceServerCheck = true)
            _isSubscriptionProcessing.value = false
            if (syncResult.isSuccess && syncResult.getOrNull()?.isEntitled == true) {
                lastFailedPayment = null
                _hasPendingPaymentVerification.value = false
                if (_currentScreen.value != LauncherScreen.ONBOARDING) {
                    _currentScreen.value = LauncherScreen.HOME
                }
            } else if (subscriptionRepository.evaluateLocalEntitlement()) {
                lastFailedPayment = null
                _hasPendingPaymentVerification.value = false
                if (_currentScreen.value != LauncherScreen.ONBOARDING) {
                    _currentScreen.value = LauncherScreen.HOME
                }
            } else {
                _subscriptionErrorMessage.value = "Unable to synchronize entitlement with server. Please retry in a moment."
            }
        }
    }

    fun handleRazorpaySuccess(paymentId: String?, paymentData: PaymentData?) {
        _isSubscriptionProcessing.value = true
        _subscriptionErrorMessage.value = null

        viewModelScope.launch {
            val pid = paymentId ?: paymentData?.paymentId ?: ""
            val signature = paymentData?.signature ?: ""

            val subIdFromPayload = paymentData?.data?.optString("razorpay_subscription_id")
            val isMonthly = pendingMonthlyIntent != null || !subIdFromPayload.isNullOrBlank()
            val subscriptionId = if (!subIdFromPayload.isNullOrBlank()) subIdFromPayload else (pendingMonthlyIntent?.subscriptionId ?: "")

            val orderIdFromPayload = paymentData?.data?.optString("razorpay_order_id")
            val orderId = if (!orderIdFromPayload.isNullOrBlank()) orderIdFromPayload else (pendingLifetimeIntent?.orderId ?: "")

            if (isMonthly && subscriptionId.isNotBlank()) {
                val verifyResult = subscriptionRepository.verifyMonthlySubscription(pid, subscriptionId, signature)
                verifyResult.fold(
                    onSuccess = {
                        _isSubscriptionProcessing.value = false
                        pendingMonthlyIntent = null
                        lastFailedPayment = null
                        _hasPendingPaymentVerification.value = false
                        if (_currentScreen.value != LauncherScreen.ONBOARDING) {
                            _currentScreen.value = LauncherScreen.HOME
                        }
                    },
                    onFailure = { error ->
                        _isSubscriptionProcessing.value = false
                        lastFailedPayment = PendingPaymentVerification(
                            paymentId = pid,
                            orderId = "",
                            subscriptionId = subscriptionId,
                            signature = signature,
                            isMonthly = true
                        )
                        _hasPendingPaymentVerification.value = true
                        _subscriptionErrorMessage.value = "Payment was received, but server verification was interrupted (${error.message}). Tap 'Retry Verification' below to activate access."
                    }
                )
            } else if (orderId.isNotBlank()) {
                val verifyResult = subscriptionRepository.verifyLifetimePayment(pid, orderId, signature)
                verifyResult.fold(
                    onSuccess = {
                        _isSubscriptionProcessing.value = false
                        pendingLifetimeIntent = null
                        lastFailedPayment = null
                        _hasPendingPaymentVerification.value = false
                        if (_currentScreen.value != LauncherScreen.ONBOARDING) {
                            _currentScreen.value = LauncherScreen.HOME
                        }
                    },
                    onFailure = { error ->
                        _isSubscriptionProcessing.value = false
                        lastFailedPayment = PendingPaymentVerification(
                            paymentId = pid,
                            orderId = orderId,
                            subscriptionId = null,
                            signature = signature,
                            isMonthly = false
                        )
                        _hasPendingPaymentVerification.value = true
                        _subscriptionErrorMessage.value = "Payment was received, but server verification was interrupted (${error.message}). Tap 'Retry Verification' below to activate access."
                    }
                )
            } else {
                val syncResult = subscriptionRepository.synchronizeEntitlement(forceServerCheck = true)
                _isSubscriptionProcessing.value = false
                if (syncResult.isSuccess && syncResult.getOrNull()?.isEntitled == true) {
                    if (_currentScreen.value != LauncherScreen.ONBOARDING) {
                        _currentScreen.value = LauncherScreen.HOME
                    }
                } else {
                    _subscriptionErrorMessage.value = "Payment completed. Syncing entitlement with server..."
                }
            }
        }
    }

    fun handleRazorpayError(errorCode: Int, response: String?, paymentData: PaymentData?) {
        _isSubscriptionProcessing.value = false
        pendingMonthlyIntent = null
        pendingLifetimeIntent = null
        val message = if (errorCode == Checkout.PAYMENT_CANCELED) {
            "Payment cancelled. You can retry when ready."
        } else {
            response?.ifBlank { null } ?: "Payment could not be completed (Error $errorCode). Please retry."
        }
        _subscriptionErrorMessage.value = message
    }

    fun clearSubscriptionError() {
        _subscriptionErrorMessage.value = null
    }

    fun retryPaymentVerification() {
        val pending = lastFailedPayment
        if (pending != null) {
            _isSubscriptionProcessing.value = true
            _subscriptionErrorMessage.value = null
            viewModelScope.launch {
                val verifyResult = if (pending.isMonthly && !pending.subscriptionId.isNullOrBlank()) {
                    subscriptionRepository.verifyMonthlySubscription(pending.paymentId, pending.subscriptionId, pending.signature)
                } else {
                    subscriptionRepository.verifyLifetimePayment(pending.paymentId, pending.orderId, pending.signature)
                }

                verifyResult.fold(
                    onSuccess = {
                        _isSubscriptionProcessing.value = false
                        lastFailedPayment = null
                        _hasPendingPaymentVerification.value = false
                        if (_currentScreen.value != LauncherScreen.ONBOARDING) {
                            _currentScreen.value = LauncherScreen.HOME
                        }
                    },
                    onFailure = { error ->
                        // Also check if server already has user entitlement set
                        val syncResult = subscriptionRepository.synchronizeEntitlement(forceServerCheck = true)
                        _isSubscriptionProcessing.value = false
                        if (syncResult.isSuccess && syncResult.getOrNull()?.isEntitled == true) {
                            lastFailedPayment = null
                            _hasPendingPaymentVerification.value = false
                            if (_currentScreen.value != LauncherScreen.ONBOARDING) {
                                _currentScreen.value = LauncherScreen.HOME
                            }
                        } else {
                            _subscriptionErrorMessage.value = "Server verification retry failed: ${error.message}. Please retry in a moment."
                        }
                    }
                )
            }
        } else {
            retrySubscriptionSync()
        }
    }

    fun retrySubscriptionSync() {
        _isSubscriptionProcessing.value = true
        _subscriptionErrorMessage.value = null
        viewModelScope.launch {
            val result = subscriptionRepository.synchronizeEntitlement(forceServerCheck = true)
            _isSubscriptionProcessing.value = false
            if (result.isSuccess && result.getOrNull()?.isEntitled == true) {
                lastFailedPayment = null
                _hasPendingPaymentVerification.value = false
                if (_currentScreen.value != LauncherScreen.ONBOARDING) {
                    _currentScreen.value = LauncherScreen.HOME
                }
            } else {
                result.onFailure { error ->
                    _subscriptionErrorMessage.value = "Sync notice: ${error.message}. Check connection and retry."
                }
            }
        }
    }

    fun cancelMonthlySubscription() {
        _isSubscriptionProcessing.value = true
        viewModelScope.launch {
            subscriptionRepository.cancelMonthlySubscription()
            _isSubscriptionProcessing.value = false
        }
    }

    fun openAndroidHomeSettings(activity: Activity) {
        openSetDefaultLauncher(activity)
    }
}
