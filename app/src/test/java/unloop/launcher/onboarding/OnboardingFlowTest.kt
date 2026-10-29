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
import unloop.launcher.ui.screens.OnboardingStep
import unloop.launcher.ui.screens.getActiveOnboardingSteps

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

    @Test
    fun testActiveOnboardingSteps_brandNewUser_includesAllStepsEndingInAllSet() {
        val steps = getActiveOnboardingSteps(
            isIntroCompleted = false,
            isEntitled = false,
            isDefaultLauncher = false
        )
        assertEquals(
            listOf(
                OnboardingStep.EXPLAIN,
                OnboardingStep.SUBSCRIPTION,
                OnboardingStep.DEFAULT_LAUNCHER,
                OnboardingStep.ALL_SET
            ),
            steps
        )
    }

    @Test
    fun testActiveOnboardingSteps_alreadySubscribedUser_completelySkipsSubscriptionScreen() {
        val steps = getActiveOnboardingSteps(
            isIntroCompleted = false,
            isEntitled = true,
            isDefaultLauncher = false
        )
        // Subscription must be completely omitted from the active onboarding steps
        assertFalse(steps.contains(OnboardingStep.SUBSCRIPTION))
        assertEquals(
            listOf(
                OnboardingStep.EXPLAIN,
                OnboardingStep.DEFAULT_LAUNCHER,
                OnboardingStep.ALL_SET
            ),
            steps
        )
    }

    @Test
    fun testActiveOnboardingSteps_alreadyDefaultLauncher_completelySkipsDefaultLauncherScreen() {
        val steps = getActiveOnboardingSteps(
            isIntroCompleted = false,
            isEntitled = false,
            isDefaultLauncher = true
        )
        // Default launcher screen must be completely omitted
        assertFalse(steps.contains(OnboardingStep.DEFAULT_LAUNCHER))
        assertEquals(
            listOf(
                OnboardingStep.EXPLAIN,
                OnboardingStep.SUBSCRIPTION,
                OnboardingStep.ALL_SET
            ),
            steps
        )
    }

    @Test
    fun testActiveOnboardingSteps_alreadySubscribedAndDefault_skipsBothToFinalAllSet() {
        val steps = getActiveOnboardingSteps(
            isIntroCompleted = false,
            isEntitled = true,
            isDefaultLauncher = true
        )
        // Both subscription and default launcher must be completely omitted
        assertFalse(steps.contains(OnboardingStep.SUBSCRIPTION))
        assertFalse(steps.contains(OnboardingStep.DEFAULT_LAUNCHER))
        assertEquals(
            listOf(
                OnboardingStep.EXPLAIN,
                OnboardingStep.ALL_SET
            ),
            steps
        )
    }

    @Test
    fun testActiveOnboardingSteps_allPriorStepsCompleted_showsOnlyFinalAllSetScreen() {
        val steps = getActiveOnboardingSteps(
            isIntroCompleted = true,
            isEntitled = true,
            isDefaultLauncher = true
        )
        assertEquals(
            listOf(OnboardingStep.ALL_SET),
            steps
        )
    }

    @Test
    fun testDefaultLauncherSet_doesNotSkipToHomeUntilAllSetCompleted() {
        val user = UserProfile("u1", "Alex", "alex@example.com")
        prefsManager.saveAuthSession(AuthSession("token_123", user))
        prefsManager.setIntroCompleted(true)

        // User sets Unloop as default launcher, but has NOT confirmed "All Set" screen yet
        assertFalse(prefsManager.isOnboardingCompleted())

        // Even with default launcher set, the initial screen must remain ONBOARDING
        val screen = determineInitialScreen(hasAuth = true, isEntitled = true, isDefaultLauncher = true)
        assertEquals("ONBOARDING", screen)
        assertFalse(prefsManager.isOnboardingCompleted())

        // Active onboarding steps must display the final ALL_SET screen
        val steps = getActiveOnboardingSteps(
            isIntroCompleted = true,
            isEntitled = true,
            isDefaultLauncher = true
        )
        assertEquals(listOf(OnboardingStep.ALL_SET), steps)

        // Only after user clicks "Let's Go" on the "All Set" screen (completing onboarding)
        prefsManager.setOnboardingCompleted(true)
        val homeScreen = determineInitialScreen(hasAuth = true, isEntitled = true, isDefaultLauncher = true)
        assertEquals("HOME", homeScreen)
    }
}
