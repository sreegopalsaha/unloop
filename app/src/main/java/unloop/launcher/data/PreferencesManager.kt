package unloop.launcher.data

import android.content.Context
import android.content.SharedPreferences
import unloop.launcher.auth.model.AuthSession
import unloop.launcher.auth.model.UserProfile
import unloop.launcher.service.FilteredNotificationItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _favoritesFlow = MutableStateFlow(getFavorites())
    val favoritesFlow: StateFlow<Set<String>> = _favoritesFlow.asStateFlow()

    private val _hiddenAppsFlow = MutableStateFlow(getHiddenApps())
    val hiddenAppsFlow: StateFlow<Set<String>> = _hiddenAppsFlow.asStateFlow()

    private val _shortVideoBlockerFlow = MutableStateFlow(isShortVideoBlockerEnabled())
    val shortVideoBlockerFlow: StateFlow<Boolean> = _shortVideoBlockerFlow.asStateFlow()

    private val _focusModeFlow = MutableStateFlow(isFocusModeEnabled())
    val focusModeFlow: StateFlow<Boolean> = _focusModeFlow.asStateFlow()

    private val _focusAppsFlow = MutableStateFlow(getFocusApps())
    val focusAppsFlow: StateFlow<Set<String>> = _focusAppsFlow.asStateFlow()

    private val _themeModeFlow = MutableStateFlow(getThemeMode())
    val themeModeFlow: StateFlow<String> = _themeModeFlow.asStateFlow()

    private val _showSystemAppsCleanupFlow = MutableStateFlow(isShowSystemAppsCleanup())
    val showSystemAppsCleanupFlow: StateFlow<Boolean> = _showSystemAppsCleanupFlow.asStateFlow()

    private val _notificationManagerFlow = MutableStateFlow(isNotificationManagerEnabled())
    val notificationManagerFlow: StateFlow<Boolean> = _notificationManagerFlow.asStateFlow()

    private val _filteredNotificationsFlow = MutableStateFlow(loadFilteredNotifications())
    val filteredNotificationsFlow: StateFlow<List<FilteredNotificationItem>> = _filteredNotificationsFlow.asStateFlow()

    private val _authSessionFlow = MutableStateFlow(loadAuthSession())
    val authSessionFlow: StateFlow<AuthSession?> = _authSessionFlow.asStateFlow()

    init {
        prefs.registerOnSharedPreferenceChangeListener { _, key ->
            when (key) {
                KEY_NOTIFICATIONS_JSON -> {
                    val updated = loadFilteredNotifications()
                    _filteredNotificationsFlow.value = updated
                }
                KEY_NOTIFICATION_MANAGER -> {
                    _notificationManagerFlow.value = isNotificationManagerEnabled()
                }
            }
        }
    }

    // --- Authentication & Session Management ---
    private fun loadAuthSession(): AuthSession? {
        val token = prefs.getString(KEY_AUTH_TOKEN, null) ?: return null
        val id = prefs.getString(KEY_USER_ID, null) ?: return null
        val email = prefs.getString(KEY_USER_EMAIL, "") ?: ""
        val name = prefs.getString(KEY_USER_NAME, "User") ?: "User"
        val picture = prefs.getString(KEY_USER_PICTURE, null)
        return AuthSession(
            token = token,
            user = UserProfile(id = id, name = name, email = email, picture = picture)
        )
    }

    fun getAuthSession(): AuthSession? {
        return _authSessionFlow.value
    }

    fun saveAuthSession(session: AuthSession) {
        prefs.edit()
            .putString(KEY_AUTH_TOKEN, session.token)
            .putString(KEY_USER_ID, session.user.id)
            .putString(KEY_USER_NAME, session.user.name)
            .putString(KEY_USER_EMAIL, session.user.email)
            .putString(KEY_USER_PICTURE, session.user.picture)
            .apply()
        _authSessionFlow.value = session
    }

    fun clearAuthSession() {
        prefs.edit()
            .remove(KEY_AUTH_TOKEN)
            .remove(KEY_USER_ID)
            .remove(KEY_USER_NAME)
            .remove(KEY_USER_EMAIL)
            .remove(KEY_USER_PICTURE)
            .apply()
        _authSessionFlow.value = null
    }

    // --- Favorites ---
    fun getFavorites(): Set<String> {
        return prefs.getStringSet(KEY_FAVORITES, emptySet()) ?: emptySet()
    }

    fun setFavorites(favorites: Set<String>) {
        prefs.edit().putStringSet(KEY_FAVORITES, favorites).apply()
        _favoritesFlow.value = favorites
    }

    fun toggleFavorite(packageName: String) {
        val current = getFavorites().toMutableSet()
        if (current.contains(packageName)) {
            current.remove(packageName)
        } else {
            current.add(packageName)
        }
        setFavorites(current)
    }

    // --- Hidden Apps ---
    fun getHiddenApps(): Set<String> {
        return prefs.getStringSet(KEY_HIDDEN_APPS, emptySet()) ?: emptySet()
    }

    fun setHiddenApps(hidden: Set<String>) {
        prefs.edit().putStringSet(KEY_HIDDEN_APPS, hidden).apply()
        _hiddenAppsFlow.value = hidden
    }

    fun setAppHidden(packageName: String, hidden: Boolean) {
        val current = getHiddenApps().toMutableSet()
        if (hidden) {
            current.add(packageName)
            // If hidden, also remove from favorites
            val currentFavs = getFavorites().toMutableSet()
            if (currentFavs.remove(packageName)) {
                setFavorites(currentFavs)
            }
        } else {
            current.remove(packageName)
        }
        setHiddenApps(current)
    }

    // --- Short-Video Blocker (ONE Global Toggle) ---
    fun isShortVideoBlockerEnabled(): Boolean {
        return prefs.getBoolean(KEY_SHORT_VIDEO_BLOCKER, false)
    }

    fun setShortVideoBlockerEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHORT_VIDEO_BLOCKER, enabled).apply()
        _shortVideoBlockerFlow.value = enabled
    }

    // --- Focus Mode ---
    fun isFocusModeEnabled(): Boolean {
        return prefs.getBoolean(KEY_FOCUS_MODE, false)
    }

    fun setFocusModeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FOCUS_MODE, enabled).apply()
        _focusModeFlow.value = enabled
    }

    fun getFocusApps(): Set<String> {
        return prefs.getStringSet(KEY_FOCUS_APPS, emptySet()) ?: emptySet()
    }

    fun setFocusApps(apps: Set<String>) {
        prefs.edit().putStringSet(KEY_FOCUS_APPS, apps).apply()
        _focusAppsFlow.value = apps
    }

    fun toggleFocusApp(packageName: String) {
        val current = getFocusApps().toMutableSet()
        if (current.contains(packageName)) {
            current.remove(packageName)
        } else {
            current.add(packageName)
        }
        setFocusApps(current)
    }

    fun getFocusDurationMinutes(): Int {
        return prefs.getInt(KEY_FOCUS_DURATION, 10)
    }

    fun setFocusDurationMinutes(minutes: Int) {
        prefs.edit().putInt(KEY_FOCUS_DURATION, minutes).apply()
    }

    // --- Notification Filter ---
    fun isNotificationManagerEnabled(): Boolean {
        return prefs.getBoolean(KEY_NOTIFICATION_MANAGER, false)
    }

    fun setNotificationManagerEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFICATION_MANAGER, enabled).apply()
        _notificationManagerFlow.value = enabled
    }

    fun getFilteredNotificationApps(): Set<String> {
        return prefs.getStringSet(KEY_NOTIFICATION_APPS, emptySet()) ?: emptySet()
    }

    fun setFilteredNotificationApps(apps: Set<String>) {
        prefs.edit().putStringSet(KEY_NOTIFICATION_APPS, apps).apply()
    }

    fun toggleFilteredNotificationApp(packageName: String) {
        val current = getFilteredNotificationApps().toMutableSet()
        if (current.contains(packageName)) {
            current.remove(packageName)
        } else {
            current.add(packageName)
        }
        setFilteredNotificationApps(current)
    }

    private fun loadFilteredNotifications(): List<FilteredNotificationItem> {
        val jsonStr = prefs.getString(KEY_NOTIFICATIONS_JSON, "[]") ?: "[]"
        val list = mutableListOf<FilteredNotificationItem>()
        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                list.add(FilteredNotificationItem.fromJson(jsonArray.getJSONObject(i)))
            }
        } catch (_: Exception) {}
        return list
    }

    fun getFilteredNotifications(): List<FilteredNotificationItem> {
        return _filteredNotificationsFlow.value
    }

    @Synchronized
    fun addFilteredNotification(item: FilteredNotificationItem) {
        val current = _filteredNotificationsFlow.value.toMutableList()
        current.removeAll { it.key == item.key }
        current.add(0, item)
        val capped = if (current.size > 100) current.take(100) else current

        val jsonArray = JSONArray()
        for (entry in capped) {
            jsonArray.put(entry.toJson())
        }
        prefs.edit().putString(KEY_NOTIFICATIONS_JSON, jsonArray.toString()).apply()
        _filteredNotificationsFlow.value = capped
    }

    @Synchronized
    fun removeFilteredNotification(key: String) {
        val current = _filteredNotificationsFlow.value.toMutableList()
        val changed = current.removeAll { it.key == key }
        if (changed) {
            val jsonArray = JSONArray()
            for (entry in current) {
                jsonArray.put(entry.toJson())
            }
            prefs.edit().putString(KEY_NOTIFICATIONS_JSON, jsonArray.toString()).apply()
            _filteredNotificationsFlow.value = current
        }
    }

    @Synchronized
    fun clearFilteredNotifications() {
        prefs.edit().putString(KEY_NOTIFICATIONS_JSON, "[]").apply()
        _filteredNotificationsFlow.value = emptyList()
    }

    fun getFilteredNotificationsJson(): String {
        return prefs.getString(KEY_NOTIFICATIONS_JSON, "[]") ?: "[]"
    }

    fun setFilteredNotificationsJson(json: String) {
        prefs.edit().putString(KEY_NOTIFICATIONS_JSON, json).apply()
        _filteredNotificationsFlow.value = loadFilteredNotifications()
    }

    // --- Battery Optimization Prompt Tracking ---
    fun hasSeenBatteryOptimizationPrompt(): Boolean {
        return prefs.getBoolean(KEY_BATTERY_PROMPT_SEEN, false)
    }

    fun setHasSeenBatteryOptimizationPrompt(seen: Boolean) {
        prefs.edit().putBoolean(KEY_BATTERY_PROMPT_SEEN, seen).apply()
    }

    // --- Theme & Appearance ---
    fun getThemeMode(): String {
        return prefs.getString(KEY_THEME_MODE, "SYSTEM") ?: "SYSTEM"
    }

    fun setThemeMode(mode: String) {
        prefs.edit().putString(KEY_THEME_MODE, mode).apply()
        _themeModeFlow.value = mode
    }

    // --- Onboarding & Intro Flow ---
    fun isIntroCompleted(): Boolean {
        return prefs.getBoolean(KEY_INTRO_COMPLETED, false)
    }

    fun setIntroCompleted(completed: Boolean) {
        prefs.edit().putBoolean(KEY_INTRO_COMPLETED, completed).apply()
    }

    fun isOnboardingCompleted(): Boolean {
        return prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
    }

    fun setOnboardingCompleted(completed: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, completed).apply()
    }

    fun hasDismissedUsageAccessPrompt(): Boolean {
        return prefs.getBoolean(KEY_USAGE_ACCESS_DISMISSED, false)
    }

    fun setHasDismissedUsageAccessPrompt(dismissed: Boolean) {
        prefs.edit().putBoolean(KEY_USAGE_ACCESS_DISMISSED, dismissed).apply()
    }

    // --- App Cleanup Preferences ---
    fun isShowSystemAppsCleanup(): Boolean {
        return prefs.getBoolean(KEY_SHOW_SYSTEM_APPS_CLEANUP, false)
    }

    fun setShowSystemAppsCleanup(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_SYSTEM_APPS_CLEANUP, show).apply()
        _showSystemAppsCleanupFlow.value = show
    }

    companion object {
        private const val PREFS_NAME = "unloop_launcher_prefs"
        private const val KEY_AUTH_TOKEN = "pref_auth_token"
        private const val KEY_USER_ID = "pref_user_id"
        private const val KEY_USER_NAME = "pref_user_name"
        private const val KEY_USER_EMAIL = "pref_user_email"
        private const val KEY_USER_PICTURE = "pref_user_picture"
        private const val KEY_FAVORITES = "pref_favorites"
        private const val KEY_HIDDEN_APPS = "pref_hidden_apps"
        private const val KEY_SHORT_VIDEO_BLOCKER = "pref_short_video_blocker"
        private const val KEY_FOCUS_MODE = "pref_focus_mode"
        private const val KEY_FOCUS_APPS = "pref_focus_apps"
        private const val KEY_FOCUS_DURATION = "pref_focus_duration"
        private const val KEY_NOTIFICATION_MANAGER = "pref_notification_manager"
        private const val KEY_NOTIFICATION_APPS = "pref_notification_apps"
        private const val KEY_NOTIFICATIONS_JSON = "pref_notifications_json"
        private const val KEY_THEME_MODE = "pref_theme_mode"
        private const val KEY_SHOW_SYSTEM_APPS_CLEANUP = "pref_show_system_apps_cleanup"
        private const val KEY_INTRO_COMPLETED = "pref_intro_completed"
        private const val KEY_ONBOARDING_COMPLETED = "pref_onboarding_completed"
        private const val KEY_BATTERY_PROMPT_SEEN = "pref_battery_prompt_seen"
        private const val KEY_USAGE_ACCESS_DISMISSED = "pref_usage_access_dismissed"

        @Volatile
        private var instance: PreferencesManager? = null

        fun getInstance(context: Context): PreferencesManager {
            return instance ?: synchronized(this) {
                instance ?: PreferencesManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
