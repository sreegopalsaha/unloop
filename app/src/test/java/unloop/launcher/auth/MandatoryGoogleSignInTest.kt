package unloop.launcher.auth

import android.content.SharedPreferences
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import unloop.launcher.auth.model.AuthSession
import unloop.launcher.auth.model.UserProfile
import unloop.launcher.auth.network.AuthApiClient
import unloop.launcher.auth.network.AuthApiException
import unloop.launcher.data.PreferencesManager
import java.io.IOException
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory SharedPreferences for unit testing without Android runtime dependencies.
 */
class FakeSharedPreferences : SharedPreferences {
    private val data = ConcurrentHashMap<String, Any>()
    private val listeners = mutableListOf<SharedPreferences.OnSharedPreferenceChangeListener>()

    override fun getAll(): MutableMap<String, *> = HashMap(data)

    override fun getString(key: String?, defValue: String?): String? =
        data[key] as? String ?: defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        (data[key] as? Set<String>)?.toMutableSet() ?: defValues

    override fun getInt(key: String?, defValue: Int): Int =
        (data[key] as? Int) ?: defValue

    override fun getLong(key: String?, defValue: Long): Long =
        (data[key] as? Long) ?: defValue

    override fun getFloat(key: String?, defValue: Float): Float =
        (data[key] as? Float) ?: defValue

    override fun getBoolean(key: String?, defValue: Boolean): Boolean =
        (data[key] as? Boolean) ?: defValue

    override fun contains(key: String?): Boolean = data.containsKey(key)

    override fun edit(): SharedPreferences.Editor = FakeEditor()

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
        listener?.let { listeners.add(it) }
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
        listeners.remove(listener)
    }

    inner class FakeEditor : SharedPreferences.Editor {
        private val temp = HashMap<String, Any?>()
        private val toRemove = mutableSetOf<String>()
        private var shouldClear = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor {
            if (key != null) {
                if (value != null) temp[key] = value else toRemove.add(key)
            }
            return this
        }

        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
            if (key != null) {
                if (values != null) temp[key] = values.toSet() else toRemove.add(key)
            }
            return this
        }

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
            if (key != null) temp[key] = value
            return this
        }

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
            if (key != null) temp[key] = value
            return this
        }

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
            if (key != null) temp[key] = value
            return this
        }

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
            if (key != null) temp[key] = value
            return this
        }

        override fun remove(key: String?): SharedPreferences.Editor {
            if (key != null) toRemove.add(key)
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            shouldClear = true
            return this
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            if (shouldClear) data.clear()
            for (key in toRemove) data.remove(key)
            for ((key, value) in temp) {
                if (value != null) data[key] = value else data.remove(key)
            }
            for (listener in listeners) {
                for (key in temp.keys) listener.onSharedPreferenceChanged(this@FakeSharedPreferences, key)
                for (key in toRemove) listener.onSharedPreferenceChanged(this@FakeSharedPreferences, key)
            }
        }
    }
}

/**
 * Fake AuthApiClient simulating backend responses (online success, offline error, 401/403).
 */
class FakeAuthApiClient : AuthApiClient() {
    var profileResult: Result<UserProfile>? = null
    var getProfileCallCount: Int = 0

    override suspend fun getProfile(token: String): Result<UserProfile> {
        getProfileCallCount++
        return profileResult ?: Result.success(UserProfile("u1", "Mock User", "mock@example.com"))
    }
}

class MandatoryGoogleSignInTest {

    private lateinit var fakePrefs: FakeSharedPreferences
    private lateinit var prefsManager: PreferencesManager
    private lateinit var fakeApiClient: FakeAuthApiClient
    private lateinit var authRepository: AuthRepository

    @Before
    fun setup() {
        fakePrefs = FakeSharedPreferences()
        prefsManager = PreferencesManager(fakePrefs)
        fakeApiClient = FakeAuthApiClient()
        authRepository = AuthRepository(
            preferencesManager = prefsManager,
            authApiClient = fakeApiClient
        )
    }

    private fun createJwt(expSeconds: Long? = null): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val header = encoder.encodeToString("""{"alg":"HS256","typ":"JWT"}""".toByteArray(Charsets.UTF_8))
        val payloadJson = if (expSeconds != null) {
            """{"sub":"user_123","exp":$expSeconds}"""
        } else {
            """{"sub":"user_123"}"""
        }
        val payload = encoder.encodeToString(payloadJson.toByteArray(Charsets.UTF_8))
        val signature = "sig_abc123"
        return "$header.$payload.$signature"
    }

    // =========================================================================
    // 1. JWT Local Expiration & Decoding
    // =========================================================================

    @Test
    fun testJwtExpiration_withExpiredToken() {
        val pastExp = 1500000000L // Year 2017
        val expiredJwt = createJwt(expSeconds = pastExp)
        val nowMillis = 1700000000000L // Year 2023

        assertTrue(AuthRepository.isJwtExpired(expiredJwt, nowMillis))
    }

    @Test
    fun testJwtExpiration_withValidFutureToken() {
        val futureExp = 2000000000L // Year 2033
        val validJwt = createJwt(expSeconds = futureExp)
        val nowMillis = 1700000000000L // Year 2023

        assertFalse(AuthRepository.isJwtExpired(validJwt, nowMillis))
    }

    @Test
    fun testJwtExpiration_withTokenWithoutExp() {
        val tokenWithoutExp = createJwt(expSeconds = null)
        assertFalse(AuthRepository.isJwtExpired(tokenWithoutExp))
    }

    @Test
    fun testJwtExpiration_withOpaqueNonJwtToken() {
        assertFalse(AuthRepository.isJwtExpired("opaque_token_string_without_dots"))
    }

    // =========================================================================
    // 2. Offline Access & Session Persistence
    // =========================================================================

    @Test
    fun testSessionPersistence_locallyStoredSessionValidWithoutNetwork() {
        val user = UserProfile("id_1", "Jane", "jane@example.com")
        val session = AuthSession(token = createJwt(expSeconds = 2500000000L), user = user)

        // Save session locally
        prefsManager.saveAuthSession(session)

        // Verify session persists in PreferencesManager
        val loadedSession = prefsManager.getAuthSession()
        assertNotNull(loadedSession)
        assertEquals("id_1", loadedSession?.user?.id)
        assertEquals("jane@example.com", loadedSession?.user?.email)

        // hasValidSession checks locally stored token without making network calls
        assertTrue(authRepository.hasValidSession())
        assertEquals(0, fakeApiClient.getProfileCallCount)
    }

    @Test
    fun testHasValidSession_expiredJwtClearsSession() {
        val user = UserProfile("id_1", "Jane", "jane@example.com")
        val expiredSession = AuthSession(token = createJwt(expSeconds = 1500000000L), user = user)

        prefsManager.saveAuthSession(expiredSession)
        assertTrue(prefsManager.getAuthSession() != null)

        // Checking validity with current time after expiration should clear session
        val nowMillis = 1700000000000L
        val isValid = authRepository.hasValidSession(currentTimeMillis = nowMillis)

        assertFalse(isValid)
        assertNull(prefsManager.getAuthSession())
    }

    // =========================================================================
    // 3. Lightweight Periodic Token Validation Strategy (Every 7 Days)
    // =========================================================================

    @Test
    fun testPeriodicValidation_notNeededWhenLessThanSevenDays() = runBlocking {
        val user = UserProfile("id_1", "Jane", "jane@example.com")
        val session = AuthSession(token = createJwt(expSeconds = 2500000000L), user = user)
        val loginTime = 1700000000000L

        prefsManager.saveAuthSession(session)
        prefsManager.setLastAuthValidationTime(loginTime)

        // Day 3 (less than 7 days)
        val threeDaysLater = loginTime + (3L * 24 * 60 * 60 * 1000L)
        val result = authRepository.validateSessionIfNeeded(currentTimeMillis = threeDaysLater)

        assertEquals(SessionValidationResult.NotNeeded, result)
        // Ensure NO network call was made
        assertEquals(0, fakeApiClient.getProfileCallCount)
        assertNotNull(prefsManager.getAuthSession())
    }

    @Test
    fun testPeriodicValidation_succeedsWhenOnlineAfterSevenDays() = runBlocking {
        val user = UserProfile("id_1", "Jane", "jane@example.com")
        val session = AuthSession(token = createJwt(expSeconds = 2500000000L), user = user)
        val loginTime = 1700000000000L

        prefsManager.saveAuthSession(session)
        prefsManager.setLastAuthValidationTime(loginTime)

        val updatedProfile = UserProfile("id_1", "Jane Updated", "jane@example.com")
        fakeApiClient.profileResult = Result.success(updatedProfile)

        // Day 8 (greater than 7 days)
        val eightDaysLater = loginTime + (8L * 24 * 60 * 60 * 1000L)
        val result = authRepository.validateSessionIfNeeded(currentTimeMillis = eightDaysLater)

        assertTrue(result is SessionValidationResult.Valid)
        assertEquals(1, fakeApiClient.getProfileCallCount)
        assertEquals("Jane Updated", prefsManager.getAuthSession()?.user?.name)
        assertEquals(eightDaysLater, prefsManager.getLastAuthValidationTime())
    }

    @Test
    fun testPeriodicValidation_offlineAllowsContinuedLocalSession() = runBlocking {
        val user = UserProfile("id_1", "Jane", "jane@example.com")
        val session = AuthSession(token = createJwt(expSeconds = 2500000000L), user = user)
        val loginTime = 1700000000000L

        prefsManager.saveAuthSession(session)
        prefsManager.setLastAuthValidationTime(loginTime)

        // Simulate device being offline (IOException)
        fakeApiClient.profileResult = Result.failure(IOException("No internet connectivity (Offline)"))

        // Day 8 (greater than 7 days)
        val eightDaysLater = loginTime + (8L * 24 * 60 * 60 * 1000L)
        val result = authRepository.validateSessionIfNeeded(currentTimeMillis = eightDaysLater)

        // Must fall back to local session, NOT logout
        assertTrue(result is SessionValidationResult.OfflineFallback)
        assertEquals(1, fakeApiClient.getProfileCallCount)
        assertNotNull(prefsManager.getAuthSession())
        assertEquals("Jane", prefsManager.getAuthSession()?.user?.name)
    }

    @Test
    fun testPeriodicValidation_backendUnauthorizedClearsSession() = runBlocking {
        val user = UserProfile("id_1", "Jane", "jane@example.com")
        val session = AuthSession(token = createJwt(expSeconds = 2500000000L), user = user)
        val loginTime = 1700000000000L

        prefsManager.saveAuthSession(session)
        prefsManager.setLastAuthValidationTime(loginTime)

        // Backend returns 401 Unauthorized (token revoked or invalidated on server)
        fakeApiClient.profileResult = Result.failure(AuthApiException("Invalid token", 401))

        // Day 8
        val eightDaysLater = loginTime + (8L * 24 * 60 * 60 * 1000L)
        val result = authRepository.validateSessionIfNeeded(currentTimeMillis = eightDaysLater)

        // Must invalidate session and clear stored credentials
        assertTrue(result is SessionValidationResult.Invalid)
        assertEquals(1, fakeApiClient.getProfileCallCount)
        assertNull(prefsManager.getAuthSession())
    }

    @Test
    fun testPeriodicValidation_localExpiredJwtClearsSessionImmediately() = runBlocking {
        val user = UserProfile("id_1", "Jane", "jane@example.com")
        val expTime = 1700000000L // seconds
        val session = AuthSession(token = createJwt(expSeconds = expTime), user = user)

        prefsManager.saveAuthSession(session)

        val currentTime = 1700500000000L // millis (well after expTime)
        val result = authRepository.validateSessionIfNeeded(currentTimeMillis = currentTime)

        assertTrue(result is SessionValidationResult.Invalid)
        assertEquals(0, fakeApiClient.getProfileCallCount)
        assertNull(prefsManager.getAuthSession())
    }

    // =========================================================================
    // 4. Mandatory Sign-In & Bypass Prevention Rules
    // =========================================================================

    @Test
    fun testBypassPrevention_initialScreenIsAlwaysOnboardingWithoutAuth() {
        // Condition: Intro marked completed, but user is NOT authenticated
        prefsManager.setIntroCompleted(true)
        prefsManager.setOnboardingCompleted(true)
        assertNull(prefsManager.getAuthSession())

        // Simulation of ViewModel determineInitialScreen logic
        fun determineInitialScreen(isDefault: Boolean): String {
            if (!authRepository.hasValidSession()) {
                return "ONBOARDING"
            }
            return if (!prefsManager.isIntroCompleted() || !isDefault) {
                "ONBOARDING"
            } else {
                "HOME"
            }
        }

        // Even if default launcher, without auth it MUST be ONBOARDING
        assertEquals("ONBOARDING", determineInitialScreen(isDefault = true))
        assertEquals("ONBOARDING", determineInitialScreen(isDefault = false))
    }

    @Test
    fun testBypassPrevention_initialScreenGoesToHomeOnlyWithAuthAndDefault() {
        val user = UserProfile("id_1", "Jane", "jane@example.com")
        val session = AuthSession(token = createJwt(expSeconds = 2500000000L), user = user)
        prefsManager.saveAuthSession(session)
        prefsManager.setIntroCompleted(true)

        fun determineInitialScreen(isDefault: Boolean): String {
            if (!authRepository.hasValidSession()) {
                return "ONBOARDING"
            }
            return if (!prefsManager.isIntroCompleted() || !isDefault) {
                "ONBOARDING"
            } else {
                "HOME"
            }
        }

        // Authenticated + Intro done + Default launcher -> HOME
        assertEquals("HOME", determineInitialScreen(isDefault = true))
        // Authenticated + Intro done + Not default -> ONBOARDING (to finish default setup)
        assertEquals("ONBOARDING", determineInitialScreen(isDefault = false))
    }

    @Test
    fun testBypassPrevention_navigationBlockedWhenUnauthenticated() {
        var currentScreen = "ONBOARDING"

        fun navigateTo(screen: String) {
            if (!authRepository.hasValidSession() && screen != "ONBOARDING") {
                currentScreen = "ONBOARDING"
                return
            }
            currentScreen = screen
        }

        navigateTo("HOME")
        assertEquals("ONBOARDING", currentScreen)

        navigateTo("ALL_APPS")
        assertEquals("ONBOARDING", currentScreen)

        navigateTo("SETTINGS")
        assertEquals("ONBOARDING", currentScreen)
    }

    @Test
    fun testBypassPrevention_cannotCompleteOnboardingWithoutAuth() {
        var currentScreen = "ONBOARDING"

        fun completeOnboarding() {
            if (!authRepository.hasValidSession()) {
                currentScreen = "ONBOARDING"
                return
            }
            prefsManager.setIntroCompleted(true)
            prefsManager.setOnboardingCompleted(true)
            currentScreen = "HOME"
        }

        completeOnboarding()
        assertEquals("ONBOARDING", currentScreen)
        assertFalse(prefsManager.isOnboardingCompleted())
    }

    @Test
    fun testBypassPrevention_checkDefaultStatusCannotProceedWithoutAuth() {
        var currentScreen = "ONBOARDING"

        fun checkDefaultStatus(isDefault: Boolean) {
            if (isDefault && authRepository.hasValidSession()) {
                prefsManager.setOnboardingCompleted(true)
                if (currentScreen == "ONBOARDING") {
                    currentScreen = "HOME"
                }
            }
        }

        // Phone makes Unloop default, but user never signed in
        checkDefaultStatus(isDefault = true)
        assertEquals("ONBOARDING", currentScreen)
        assertFalse(prefsManager.isOnboardingCompleted())
    }

    @Test
    fun testBypassPrevention_initialOnboardingPageCannotSkipWithoutAuth() {
        fun getInitialOnboardingPage(isDefault: Boolean): Int {
            if (!authRepository.hasValidSession()) {
                return 0
            }
            return if (prefsManager.isIntroCompleted() && !isDefault) 2 else 1
        }

        // Without auth, even if intro was marked done, user starts at 0 (cannot skip to page 2)
        prefsManager.setIntroCompleted(true)
        assertEquals(0, getInitialOnboardingPage(isDefault = false))

        // With auth, user who read intro can jump directly to default launcher setup
        val session = AuthSession(createJwt(expSeconds = 2500000000L), UserProfile("1", "A", "a@b.com"))
        prefsManager.saveAuthSession(session)
        assertEquals(2, getInitialOnboardingPage(isDefault = false))

        // With auth, but intro not completed yet, user is on page 1 ("Welcome to Unloop")
        prefsManager.setIntroCompleted(false)
        assertEquals(1, getInitialOnboardingPage(isDefault = false))
    }

    @Test
    fun testBypassPrevention_logoutClearsSessionAndForcesOnboarding() {
        val user = UserProfile("id_1", "Jane", "jane@example.com")
        val session = AuthSession(token = createJwt(expSeconds = 2500000000L), user = user)
        prefsManager.saveAuthSession(session)

        assertTrue(authRepository.hasValidSession())

        authRepository.logout()

        assertFalse(authRepository.hasValidSession())
        assertNull(prefsManager.getAuthSession())
        assertEquals(0L, prefsManager.getLastAuthValidationTime())
    }
}
