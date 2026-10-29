package unloop.launcher.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import unloop.launcher.auth.FakeSharedPreferences
import unloop.launcher.auth.model.AuthSession
import unloop.launcher.auth.model.UserProfile
import unloop.launcher.data.PreferencesManager

class OnboardingFlowTest {

    private lateinit var fakePrefs: FakeSharedPreferences
    private lateinit var prefsManager: PreferencesManager

    @Before
    fun setUp() {
        fakePrefs = FakeSharedPreferences()
        prefsManager = PreferencesManager(fakePrefs)
    }

    /**
     * Helper mimicking LauncherViewModel.determineInitialScreen()
     */
    private fun determineInitialScreen(
        hasAuth: Boolean,
        isEntitled: Boolean,
        isDefaultLauncher: Boolean
    ): String {
        if (!hasAuth) {
            return "ONBOARDING"
        }
        if (!isEntitled) {
            return "ONBOARDING"
        }
        if (!prefsManager.isOnboardingCompleted()) {
            if (isDefaultLauncher && prefsManager.isIntroCompleted()) {
                prefsManager.setOnboardingCompleted(true)
                return "HOME"
            }
            return "ONBOARDING"
        }
        return "HOME"
    }

    /**
     * Helper mimicking LauncherViewModel.getInitialOnboardingPage()
     */
    private fun getInitialOnboardingPage(
        hasAuth: Boolean,
        isEntitled: Boolean
    ): Int {
        if (!hasAuth) {
            return 0
        }
        if (!prefsManager.isIntroCompleted()) {
            return 1
        }
        if (!isEntitled) {
            return 2
        }
        return 3
    }

    @Test
    fun testFirstLaunch_startsOnGoogleSignInPage() {
        assertFalse(prefsManager.isIntroCompleted())
        assertFalse(prefsManager.isOnboardingCompleted())

        val screen = determineInitialScreen(hasAuth = false, isEntitled = false, isDefaultLauncher = false)
        val page = getInitialOnboardingPage(hasAuth = false, isEntitled = false)

        assertEquals("ONBOARDING", screen)
        assertEquals(0, page)
    }

    @Test
    fun testAuthenticatedWithoutIntro_startsOnWelcomePage() {
        val user = UserProfile("u1", "Alex", "alex@example.com")
        prefsManager.saveAuthSession(AuthSession("token_123", user))
        assertFalse(prefsManager.isIntroCompleted())

        val screen = determineInitialScreen(hasAuth = true, isEntitled = false, isDefaultLauncher = false)
        val page = getInitialOnboardingPage(hasAuth = true, isEntitled = false)

        assertEquals("ONBOARDING", screen)
        assertEquals(1, page)
    }

    @Test
    fun testIntroCompletedWithoutSubscription_startsOnSubscriptionPage() {
        val user = UserProfile("u1", "Alex", "alex@example.com")
        prefsManager.saveAuthSession(AuthSession("token_123", user))
        prefsManager.setIntroCompleted(true)

        val screen = determineInitialScreen(hasAuth = true, isEntitled = false, isDefaultLauncher = false)
        val page = getInitialOnboardingPage(hasAuth = true, isEntitled = false)

        assertEquals("ONBOARDING", screen)
        assertEquals(2, page)
    }

    @Test
    fun testSuccessfulSubscription_advancesToDefaultLauncherPageWithoutSkippingToHome() {
        val user = UserProfile("u1", "Alex", "alex@example.com")
        prefsManager.saveAuthSession(AuthSession("token_123", user))
        prefsManager.setIntroCompleted(true)

        // Payment succeeds, isEntitled becomes true, but user has not completed default setup yet
        val screen = determineInitialScreen(hasAuth = true, isEntitled = true, isDefaultLauncher = false)
        val page = getInitialOnboardingPage(hasAuth = true, isEntitled = true)

        // The app MUST remain in ONBOARDING on Page 3 (Default Launcher setup)
        assertEquals("ONBOARDING", screen)
        assertEquals(3, page)
    }

    @Test
    fun testAlreadyEntitledUser_skipsSubscriptionPageDirectlyToDefaultLauncher() {
        val user = UserProfile("u1", "Alex", "alex@example.com")
        prefsManager.saveAuthSession(AuthSession("token_123", user))
        prefsManager.setIntroCompleted(true)

        // User already has entitlement (e.g. lifetime purchaser signing into a new device)
        val page = getInitialOnboardingPage(hasAuth = true, isEntitled = true)

        // Must jump directly to Page 3 (Default Launcher setup), skipping Page 2
        assertEquals(3, page)
    }

    @Test
    fun testDefaultLauncherSetupCompletion_marksOnboardingCompleteAndNavigatesToHome() {
        val user = UserProfile("u1", "Alex", "alex@example.com")
        prefsManager.saveAuthSession(AuthSession("token_123", user))
        prefsManager.setIntroCompleted(true)

        // User sets default launcher or taps 'Continue without setting default'
        prefsManager.setOnboardingCompleted(true)

        val screen = determineInitialScreen(hasAuth = true, isEntitled = true, isDefaultLauncher = true)
        assertEquals("HOME", screen)
        assertTrue(prefsManager.isOnboardingCompleted())
    }

    @Test
    fun testReturningUserWithCompletedOnboarding_opensDirectlyToHomeEvenIfNotDefault() {
        val user = UserProfile("u1", "Alex", "alex@example.com")
        prefsManager.saveAuthSession(AuthSession("token_123", user))
        prefsManager.setIntroCompleted(true)
        prefsManager.setOnboardingCompleted(true)

        // Returning user previously completed onboarding, but device is not default launcher currently
        val screen = determineInitialScreen(hasAuth = true, isEntitled = true, isDefaultLauncher = false)

        // Must not force the user through onboarding again
        assertEquals("HOME", screen)
    }

    @Test
    fun testLogout_resetsOnboardingState() {
        val user = UserProfile("u1", "Alex", "alex@example.com")
        prefsManager.saveAuthSession(AuthSession("token_123", user))
        prefsManager.setIntroCompleted(true)
        prefsManager.setOnboardingCompleted(true)

        // Perform logout
        prefsManager.clearAuthSession()
        prefsManager.setIntroCompleted(false)
        prefsManager.setOnboardingCompleted(false)

        assertFalse(prefsManager.isIntroCompleted())
        assertFalse(prefsManager.isOnboardingCompleted())

        val screen = determineInitialScreen(hasAuth = false, isEntitled = false, isDefaultLauncher = false)
        val page = getInitialOnboardingPage(hasAuth = false, isEntitled = false)

        assertEquals("ONBOARDING", screen)
        assertEquals(0, page)
    }

    @Test
    fun testProcessRestartMidOnboarding_resumesAtExactIncompleteStep() {
        val user = UserProfile("u1", "Alex", "alex@example.com")
        prefsManager.saveAuthSession(AuthSession("token_123", user))

        // Step 1: User viewed intro, but closed app before paying
        prefsManager.setIntroCompleted(true)
        assertEquals(2, getInitialOnboardingPage(hasAuth = true, isEntitled = false))

        // Step 2: User paid, but OS killed app before setting default launcher
        assertEquals(3, getInitialOnboardingPage(hasAuth = true, isEntitled = true))
        assertEquals("ONBOARDING", determineInitialScreen(hasAuth = true, isEntitled = true, isDefaultLauncher = false))
    }
}
