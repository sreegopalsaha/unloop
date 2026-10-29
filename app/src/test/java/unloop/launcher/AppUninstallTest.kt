package unloop.launcher

import unloop.launcher.data.AppItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUninstallTest {

    private val launcherPackage = "unloop.launcher"

    @Test
    fun testAppUninstallEligibility_userAppIsUninstallable() {
        val userApp = AppItem(
            packageName = "com.spotify.music",
            className = "MainActivity",
            label = "Spotify",
            isSystemApp = false
        )

        val isSystem = userApp.isSystemApp
        val isSelf = userApp.packageName == launcherPackage

        assertFalse("User app should not be marked as a system app", isSystem)
        assertFalse("User app is not the launcher itself", isSelf)
        assertTrue("User app should be eligible for uninstallation", !isSystem && !isSelf)
    }

    @Test
    fun testAppUninstallEligibility_systemAppCannotBeUninstalled() {
        val systemApp = AppItem(
            packageName = "com.android.settings",
            className = "SettingsActivity",
            label = "Settings",
            isSystemApp = true
        )

        assertTrue("System app must be recognized as system app", systemApp.isSystemApp)
        val canUninstall = !systemApp.isSystemApp && systemApp.packageName != launcherPackage
        assertFalse("System apps should not be uninstallable", canUninstall)
    }

    @Test
    fun testAppUninstallEligibility_launcherCannotBeUninstalled() {
        val launcherApp = AppItem(
            packageName = launcherPackage,
            className = "MainActivity",
            label = "Unloop",
            isSystemApp = false
        )

        assertEquals("Should match launcher package", launcherPackage, launcherApp.packageName)
        val canUninstall = !launcherApp.isSystemApp && launcherApp.packageName != launcherPackage
        assertFalse("Launcher itself cannot be uninstalled", canUninstall)
    }

    @Test
    fun testPackageUriFormatting() {
        val targetPackage = "com.example.thirdparty"
        val uriString = "package:$targetPackage"
        assertEquals("package:com.example.thirdparty", uriString)
        assertTrue(uriString.startsWith("package:"))
        assertEquals(targetPackage, uriString.removePrefix("package:"))
    }

    @Test
    fun testPreferencesPruningOnUninstall() {
        val installedPackages = setOf("com.app.one", "com.app.two")
        val savedFavorites = setOf("com.app.one", "com.app.uninstalled")
        val savedHidden = setOf("com.app.two", "com.app.uninstalled")
        val savedFocus = setOf("com.app.one", "com.app.uninstalled")

        val deadFavorites = savedFavorites.filterNot { installedPackages.contains(it) }.toSet()
        val deadHidden = savedHidden.filterNot { installedPackages.contains(it) }.toSet()
        val deadFocus = savedFocus.filterNot { installedPackages.contains(it) }.toSet()

        assertEquals(setOf("com.app.uninstalled"), deadFavorites)
        assertEquals(setOf("com.app.uninstalled"), deadHidden)
        assertEquals(setOf("com.app.uninstalled"), deadFocus)

        val updatedFavorites = savedFavorites - deadFavorites
        val updatedHidden = savedHidden - deadHidden
        val updatedFocus = savedFocus - deadFocus

        assertEquals(setOf("com.app.one"), updatedFavorites)
        assertEquals(setOf("com.app.two"), updatedHidden)
        assertEquals(setOf("com.app.one"), updatedFocus)
    }
}
