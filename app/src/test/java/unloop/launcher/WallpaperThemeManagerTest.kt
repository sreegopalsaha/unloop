package unloop.launcher

import android.app.WallpaperManager
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import unloop.launcher.auth.FakeSharedPreferences
import unloop.launcher.data.PreferencesManager
import unloop.launcher.util.WallpaperThemeManager
import java.io.File
import java.io.OutputStream

class WallpaperThemeManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var fakePrefs: FakeSharedPreferences
    private lateinit var prefsManager: PreferencesManager
    private lateinit var fakeContext: FakeContext
    private lateinit var filesDir: File

    private lateinit var fakeAdapter: FakeWallpaperAdapter
    private lateinit var fakeBitmapProvider: FakeBitmapProvider
    private lateinit var wallpaperThemeManager: WallpaperThemeManager

    class FakeContext(private val testFilesDir: File) : ContextWrapper(null) {
        override fun getFilesDir(): File = testFilesDir
        override fun getApplicationContext(): Context = this
    }

    class FakeWallpaperAdapter : WallpaperThemeManager.WallpaperAdapter {
        var isSupported: Boolean = true
        var isAllowed: Boolean = true
        var desiredWidth: Int = 1080
        var desiredHeight: Int = 1920

        var simulatedFileBitmap: Bitmap? = null
        var simulatedDrawableBitmap: Bitmap? = null
        var shouldThrowSecurityExceptionOnRead: Boolean = false

        val setBitmapCalls = mutableListOf<Pair<Bitmap, Int>>()

        override fun isWallpaperSupported(): Boolean = isSupported
        override fun isSetWallpaperAllowed(): Boolean = isAllowed
        override fun getDesiredMinimumWidth(): Int = desiredWidth
        override fun getDesiredMinimumHeight(): Int = desiredHeight

        override fun setBitmap(bitmap: Bitmap, which: Int) {
            setBitmapCalls.add(bitmap to which)
        }

        override fun getWallpaperFile(which: Int): Bitmap? {
            if (shouldThrowSecurityExceptionOnRead) {
                throw SecurityException("Access to wallpaper file denied by system")
            }
            return simulatedFileBitmap
        }

        override fun getWallpaperDrawable(): Bitmap? {
            if (shouldThrowSecurityExceptionOnRead) {
                throw SecurityException("Access to wallpaper drawable denied by system")
            }
            return simulatedDrawableBitmap
        }
    }

    class FakeBitmapProvider : WallpaperThemeManager.BitmapProvider {
        val createdBitmaps = mutableListOf<Triple<Int, Int, Int>>()
        var simulatedDecodedBitmap: Bitmap? = null

        private fun newBitmapInstance(): Bitmap {
            val constructor = Bitmap::class.java.getDeclaredConstructor()
            constructor.isAccessible = true
            return constructor.newInstance()
        }

        override fun createSolidColorBitmap(width: Int, height: Int, color: Int): Bitmap {
            createdBitmaps.add(Triple(width, height, color))
            return newBitmapInstance()
        }

        override fun compressBitmapToPng(bitmap: Bitmap, outputStream: OutputStream): Boolean {
            outputStream.write("FAKE_PNG_BYTES".toByteArray())
            return true
        }

        override fun decodeBitmapFromFile(filePath: String): Bitmap? {
            return simulatedDecodedBitmap ?: newBitmapInstance()
        }
    }

    private fun createTestBitmap(): Bitmap {
        val constructor = Bitmap::class.java.getDeclaredConstructor()
        constructor.isAccessible = true
        return constructor.newInstance()
    }

    @Before
    fun setUp() {
        fakePrefs = FakeSharedPreferences()
        prefsManager = PreferencesManager(fakePrefs)

        filesDir = tempFolder.newFolder("files")
        fakeContext = FakeContext(filesDir)

        fakeAdapter = FakeWallpaperAdapter()
        fakeBitmapProvider = FakeBitmapProvider()

        wallpaperThemeManager = WallpaperThemeManager(
            context = fakeContext,
            prefs = prefsManager,
            ioDispatcher = Dispatchers.Unconfined,
            wallpaperAdapter = fakeAdapter,
            bitmapProvider = fakeBitmapProvider
        )
    }

    @Test
    fun testLightMode_appliesSolidWhiteToSystemOnly() = runBlocking {
        prefsManager.setThemeMode("LIGHT")
        prefsManager.setThemeWallpaperSyncEnabled(true)

        val applied = wallpaperThemeManager.applyThemeWallpaper(
            isDark = false,
            isDefaultLauncher = true
        )

        assertTrue(applied)
        assertEquals(1, fakeAdapter.setBitmapCalls.size)
        val (_, which) = fakeAdapter.setBitmapCalls[0]
        assertEquals(WallpaperManager.FLAG_SYSTEM, which)
        assertEquals(0, which and WallpaperManager.FLAG_LOCK)

        // Target color for light mode is pure white
        assertEquals(1, fakeBitmapProvider.createdBitmaps.size)
        val (_, _, color) = fakeBitmapProvider.createdBitmaps[0]
        assertEquals(Color.WHITE, color)

        assertTrue(prefsManager.wasThemeWallpaperApplied())
        assertEquals(Color.WHITE, prefsManager.getLastAppliedWallpaperColor())
    }

    @Test
    fun testDarkMode_appliesSolidBlackToSystemOnly() = runBlocking {
        prefsManager.setThemeMode("DARK")
        prefsManager.setThemeWallpaperSyncEnabled(true)

        val applied = wallpaperThemeManager.applyThemeWallpaper(
            isDark = true,
            isDefaultLauncher = true
        )

        assertTrue(applied)
        assertEquals(1, fakeAdapter.setBitmapCalls.size)
        val (_, which) = fakeAdapter.setBitmapCalls[0]
        assertEquals(WallpaperManager.FLAG_SYSTEM, which)
        assertEquals(0, which and WallpaperManager.FLAG_LOCK)

        // Target color for dark mode is pure black
        assertEquals(1, fakeBitmapProvider.createdBitmaps.size)
        val (_, _, color) = fakeBitmapProvider.createdBitmaps[0]
        assertEquals(Color.BLACK, color)

        assertTrue(prefsManager.wasThemeWallpaperApplied())
        assertEquals(Color.BLACK, prefsManager.getLastAppliedWallpaperColor())
    }

    @Test
    fun testPreserveOriginalWallpaper_savesBackup_whenSupported() = runBlocking {
        val originalMockBitmap = createTestBitmap()
        fakeAdapter.simulatedFileBitmap = originalMockBitmap

        val preserved = wallpaperThemeManager.preserveOriginalWallpaper()

        assertTrue(preserved)
        assertTrue(prefsManager.hasSavedOriginalWallpaper())

        val backupFile = prefsManager.getSavedOriginalWallpaperFile(fakeContext)
        assertTrue(backupFile.exists())
        assertTrue(backupFile.length() > 0)
    }

    @Test
    fun testRestoreOriginalWallpaper_restoresToFlagSystem_andCleansUp() = runBlocking {
        // First simulate preserved original wallpaper
        val backupFile = prefsManager.getSavedOriginalWallpaperFile(fakeContext)
        backupFile.writeText("ORIGINAL_WALLPAPER_DATA")
        prefsManager.setHasSavedOriginalWallpaper(true)
        prefsManager.setWasThemeWallpaperApplied(true)
        prefsManager.setLastAppliedWallpaperColor(Color.BLACK)

        val restoredMockBitmap = createTestBitmap()
        fakeBitmapProvider.simulatedDecodedBitmap = restoredMockBitmap

        val restored = wallpaperThemeManager.restoreOriginalWallpaper()

        assertTrue(restored)
        assertEquals(1, fakeAdapter.setBitmapCalls.size)
        val (restoredBmp, which) = fakeAdapter.setBitmapCalls[0]
        assertEquals(restoredMockBitmap, restoredBmp)
        assertEquals(WallpaperManager.FLAG_SYSTEM, which)
        assertEquals(0, which and WallpaperManager.FLAG_LOCK)

        // Must clean up backup file and state
        assertFalse(backupFile.exists())
        assertFalse(prefsManager.hasSavedOriginalWallpaper())
        assertFalse(prefsManager.wasThemeWallpaperApplied())
        assertNull(prefsManager.getLastAppliedWallpaperColor())
    }

    @Test
    fun testRestoreOriginalWallpaper_withoutSavedBackup_doesNotAssumeClear() = runBlocking {
        // If no backup was preserved, it MUST NOT call clear() or assume clear restores user wallpaper
        prefsManager.setHasSavedOriginalWallpaper(false)
        prefsManager.setWasThemeWallpaperApplied(true)

        val restored = wallpaperThemeManager.restoreOriginalWallpaper()

        assertFalse(restored)
        // No setBitmap call made
        assertEquals(0, fakeAdapter.setBitmapCalls.size)
        assertFalse(prefsManager.wasThemeWallpaperApplied())
        assertNull(prefsManager.getLastAppliedWallpaperColor())
    }

    @Test
    fun testSwitchAwayFromDefaultLauncher_triggersRestore() = runBlocking {
        // Set up theme wallpaper applied while default
        prefsManager.setThemeWallpaperSyncEnabled(true)
        prefsManager.setWasThemeWallpaperApplied(true)
        prefsManager.setHasSavedOriginalWallpaper(true)
        val backupFile = prefsManager.getSavedOriginalWallpaperFile(fakeContext)
        backupFile.writeText("ORIGINAL_WALLPAPER_DATA")

        // User switches away from default launcher
        val result = wallpaperThemeManager.applyThemeWallpaper(
            isDark = true,
            isDefaultLauncher = false
        )

        assertFalse(result)
        // Original wallpaper restored to FLAG_SYSTEM
        assertEquals(1, fakeAdapter.setBitmapCalls.size)
        assertEquals(WallpaperManager.FLAG_SYSTEM, fakeAdapter.setBitmapCalls[0].second)
        assertFalse(prefsManager.wasThemeWallpaperApplied())
    }

    @Test
    fun testDisableFeature_triggersRestore() = runBlocking {
        // Set up theme wallpaper applied
        prefsManager.setWasThemeWallpaperApplied(true)
        prefsManager.setHasSavedOriginalWallpaper(true)
        val backupFile = prefsManager.getSavedOriginalWallpaperFile(fakeContext)
        backupFile.writeText("ORIGINAL_WALLPAPER_DATA")

        // Disable feature
        prefsManager.setThemeWallpaperSyncEnabled(false)

        val result = wallpaperThemeManager.applyThemeWallpaper(
            isDark = true,
            isDefaultLauncher = true
        )

        assertFalse(result)
        // Original wallpaper restored
        assertEquals(1, fakeAdapter.setBitmapCalls.size)
        assertEquals(WallpaperManager.FLAG_SYSTEM, fakeAdapter.setBitmapCalls[0].second)
        assertFalse(prefsManager.wasThemeWallpaperApplied())
    }

    @Test
    fun testSecurityException_onReadingOriginalWallpaper_handledSafely() = runBlocking {
        fakeAdapter.shouldThrowSecurityExceptionOnRead = true

        val preserved = wallpaperThemeManager.preserveOriginalWallpaper()

        // Should return false gracefully without throwing
        assertFalse(preserved)
        assertFalse(prefsManager.hasSavedOriginalWallpaper())

        // And theme wallpaper application can still proceed safely
        val applied = wallpaperThemeManager.applyThemeWallpaper(
            isDark = true,
            isDefaultLauncher = true
        )
        assertTrue(applied)
        assertEquals(1, fakeAdapter.setBitmapCalls.size)
        assertEquals(WallpaperManager.FLAG_SYSTEM, fakeAdapter.setBitmapCalls[0].second)
    }

    @Test
    fun testIdempotency_sameThemeColorNotReapplied() = runBlocking {
        prefsManager.setThemeWallpaperSyncEnabled(true)

        // First apply
        wallpaperThemeManager.applyThemeWallpaper(isDark = true, isDefaultLauncher = true)
        assertEquals(1, fakeAdapter.setBitmapCalls.size)

        // Second apply with identical effective theme
        wallpaperThemeManager.applyThemeWallpaper(isDark = true, isDefaultLauncher = true)
        // setBitmap should not be called again
        assertEquals(1, fakeAdapter.setBitmapCalls.size)

        // Theme changes to light
        wallpaperThemeManager.applyThemeWallpaper(isDark = false, isDefaultLauncher = true)
        assertEquals(2, fakeAdapter.setBitmapCalls.size)
        assertEquals(Color.WHITE, fakeBitmapProvider.createdBitmaps.last().third)
    }

    @Test
    fun testNeverChangesLockScreenWallpaperAcrossOperations() = runBlocking {
        prefsManager.setThemeWallpaperSyncEnabled(true)

        // Apply dark
        wallpaperThemeManager.applyThemeWallpaper(isDark = true, isDefaultLauncher = true)
        // Apply light
        wallpaperThemeManager.applyThemeWallpaper(isDark = false, isDefaultLauncher = true)

        for ((_, which) in fakeAdapter.setBitmapCalls) {
            assertEquals("Flag must strictly be FLAG_SYSTEM", WallpaperManager.FLAG_SYSTEM, which)
            assertEquals("Flag must NEVER include FLAG_LOCK", 0, which and WallpaperManager.FLAG_LOCK)
        }
    }
}
