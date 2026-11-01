package unloop.launcher.util

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import unloop.launcher.data.PreferencesManager
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

class WallpaperThemeManager(
    private val context: Context,
    private val prefs: PreferencesManager = PreferencesManager.getInstance(context),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val wallpaperAdapter: WallpaperAdapter = AndroidWallpaperAdapter(context),
    private val bitmapProvider: BitmapProvider = DefaultBitmapProvider()
) {

    private val mutex = Mutex()

    /**
     * Interface abstracting WallpaperManager operations for robustness, safety, and testing.
     */
    interface WallpaperAdapter {
        fun isWallpaperSupported(): Boolean
        fun isSetWallpaperAllowed(): Boolean
        fun getDesiredMinimumWidth(): Int
        fun getDesiredMinimumHeight(): Int
        fun setBitmap(bitmap: Bitmap, which: Int)
        fun getWallpaperFile(which: Int): Bitmap?
        fun getWallpaperDrawable(): Bitmap?
    }

    /**
     * Interface abstracting bitmap creation and I/O for clean testing.
     */
    interface BitmapProvider {
        fun createSolidColorBitmap(width: Int, height: Int, color: Int): Bitmap
        fun compressBitmapToPng(bitmap: Bitmap, outputStream: OutputStream): Boolean
        fun decodeBitmapFromFile(filePath: String): Bitmap?
    }

    class DefaultBitmapProvider : BitmapProvider {
        override fun createSolidColorBitmap(width: Int, height: Int, color: Int): Bitmap {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(color)
            return bitmap
        }

        override fun compressBitmapToPng(bitmap: Bitmap, outputStream: OutputStream): Boolean {
            return bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        }

        override fun decodeBitmapFromFile(filePath: String): Bitmap? {
            return BitmapFactory.decodeFile(filePath)
        }
    }

    class AndroidWallpaperAdapter(private val context: Context) : WallpaperAdapter {
        private val wallpaperManager: WallpaperManager by lazy {
            WallpaperManager.getInstance(context)
        }

        override fun isWallpaperSupported(): Boolean {
            return try {
                wallpaperManager.isWallpaperSupported
            } catch (e: Throwable) {
                false
            }
        }

        override fun isSetWallpaperAllowed(): Boolean {
            return try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    wallpaperManager.isSetWallpaperAllowed
                } else {
                    true
                }
            } catch (e: Throwable) {
                false
            }
        }

        override fun getDesiredMinimumWidth(): Int {
            return try {
                wallpaperManager.desiredMinimumWidth
            } catch (e: Throwable) {
                0
            }
        }

        override fun getDesiredMinimumHeight(): Int {
            return try {
                wallpaperManager.desiredMinimumHeight
            } catch (e: Throwable) {
                0
            }
        }

        override fun setBitmap(bitmap: Bitmap, which: Int) {
            // Strictly target the specified flag (always FLAG_SYSTEM, NEVER FLAG_LOCK)
            wallpaperManager.setBitmap(bitmap, null, true, which)
        }

        override fun getWallpaperFile(which: Int): Bitmap? {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try {
                    val pfd = wallpaperManager.getWallpaperFile(which)
                    if (pfd != null) {
                        return pfd.use {
                            BitmapFactory.decodeFileDescriptor(it.fileDescriptor)
                        }
                    }
                } catch (e: SecurityException) {
                    Log.w(TAG, "Cannot read wallpaper file due to security restrictions: ${e.message}")
                } catch (e: Throwable) {
                    Log.w(TAG, "Failed reading wallpaper file: ${e.message}")
                }
            }
            return null
        }

        override fun getWallpaperDrawable(): Bitmap? {
            try {
                val drawable = wallpaperManager.drawable ?: wallpaperManager.peekDrawable()
                if (drawable != null) {
                    if (drawable is BitmapDrawable && drawable.bitmap != null) {
                        return drawable.bitmap.copy(Bitmap.Config.ARGB_8888, false)
                    }
                    val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: 1080
                    val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: 1920
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bmp)
                    drawable.setBounds(0, 0, w, h)
                    drawable.draw(canvas)
                    return bmp
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "Cannot read wallpaper drawable due to security restrictions: ${e.message}")
            } catch (e: Throwable) {
                Log.w(TAG, "Failed reading wallpaper drawable: ${e.message}")
            }
            return null
        }
    }

    /**
     * Checks if wallpaper manipulation is supported and permitted on this device.
     */
    fun isSupported(): Boolean {
        return wallpaperAdapter.isWallpaperSupported() && wallpaperAdapter.isSetWallpaperAllowed()
    }

    /**
     * Preserves the original home screen wallpaper to local app storage.
     * Guaranteed safe: if reading wallpaper is restricted by Android OS, catches gracefully without crashing.
     */
    suspend fun preserveOriginalWallpaper(): Boolean = withContext(ioDispatcher) {
        mutex.withLock {
            preserveOriginalWallpaperInternal()
        }
    }

    /**
     * Restores the user's original wallpaper if it was previously preserved.
     * Never calls WallpaperManager.clear() as per strict requirement:
     * "Do not assume clearing the wallpaper restores the original one."
     */
    suspend fun restoreOriginalWallpaper(): Boolean = withContext(ioDispatcher) {
        mutex.withLock {
            restoreOriginalWallpaperInternal()
        }
    }

    /**
     * Applies solid white (light mode) or solid black (dark mode) to home screen wallpaper only.
     * Never touches lock screen wallpaper.
     */
    suspend fun applyThemeWallpaper(
        isDark: Boolean,
        isDefaultLauncher: Boolean
    ): Boolean = withContext(ioDispatcher) {
        mutex.withLock {
            if (!isDefaultLauncher) {
                if (prefs.wasThemeWallpaperApplied()) {
                    restoreOriginalWallpaperInternal()
                }
                return@withLock false
            }

            if (!isSupported()) {
                return@withLock false
            }

            val targetColor = if (isDark) Color.BLACK else Color.WHITE
            val lastColor = prefs.getLastAppliedWallpaperColor()
            val wasApplied = prefs.wasThemeWallpaperApplied()

            if (wasApplied && lastColor != null && lastColor == targetColor) {
                // Color already matches current theme
                return@withLock true
            }

            // Attempt to preserve original wallpaper before overwriting for the first time
            if (!prefs.hasSavedOriginalWallpaper()) {
                preserveOriginalWallpaperInternal()
            }

            try {
                var width = wallpaperAdapter.getDesiredMinimumWidth()
                var height = wallpaperAdapter.getDesiredMinimumHeight()
                if (width <= 0 || height <= 0) {
                    val dm = context.resources.displayMetrics
                    width = dm?.widthPixels?.coerceAtLeast(1080) ?: 1080
                    height = dm?.heightPixels?.coerceAtLeast(1920) ?: 1920
                }

                val bitmap = bitmapProvider.createSolidColorBitmap(width, height, targetColor)

                // Strictly FLAG_SYSTEM: NEVER touches lock screen wallpaper!
                wallpaperAdapter.setBitmap(bitmap, WallpaperManager.FLAG_SYSTEM)
                try {
                    bitmap.recycle()
                } catch (_: Throwable) {}

                prefs.setLastAppliedWallpaperColor(targetColor)
                prefs.setWasThemeWallpaperApplied(true)
                Log.i(TAG, "Applied solid ${if (isDark) "black" else "white"} theme wallpaper to home screen")
                return@withLock true
            } catch (e: Throwable) {
                Log.e(TAG, "Failed applying theme wallpaper: ${e.message}", e)
                return@withLock false
            }
        }
    }

    private fun preserveOriginalWallpaperInternal(): Boolean {
        if (prefs.hasSavedOriginalWallpaper()) {
            val file = prefs.getSavedOriginalWallpaperFile(context)
            if (file.exists() && file.length() > 0) {
                return true
            }
        }

        if (!isSupported()) {
            return false
        }

        // Try reading via WallpaperFile (FLAG_SYSTEM) first
        var originalBitmap: Bitmap? = try {
            wallpaperAdapter.getWallpaperFile(WallpaperManager.FLAG_SYSTEM)
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot read wallpaper file due to security restrictions: ${e.message}")
            null
        } catch (e: Throwable) {
            Log.w(TAG, "Failed reading wallpaper file: ${e.message}")
            null
        }

        // Fallback to Drawable/peekDrawable if getWallpaperFile is null
        if (originalBitmap == null) {
            originalBitmap = try {
                wallpaperAdapter.getWallpaperDrawable()
            } catch (e: SecurityException) {
                Log.w(TAG, "Cannot read wallpaper drawable due to security restrictions: ${e.message}")
                null
            } catch (e: Throwable) {
                Log.w(TAG, "Failed reading wallpaper drawable: ${e.message}")
                null
            }
        }

        if (originalBitmap != null) {
            try {
                val file = prefs.getSavedOriginalWallpaperFile(context)
                FileOutputStream(file).use { out ->
                    bitmapProvider.compressBitmapToPng(originalBitmap, out)
                }
                prefs.setHasSavedOriginalWallpaper(true)
                Log.i(TAG, "Original wallpaper preserved successfully (${file.length()} bytes)")
                return true
            } catch (e: Throwable) {
                Log.e(TAG, "Failed saving original wallpaper: ${e.message}", e)
            } finally {
                try {
                    originalBitmap.recycle()
                } catch (_: Throwable) {}
            }
        }
        return false
    }

    private fun restoreOriginalWallpaperInternal(): Boolean {
        if (!prefs.hasSavedOriginalWallpaper()) {
            // Cannot restore without preserved backup.
            // Never clear() because that resets to factory default, destroying user setup.
            prefs.setWasThemeWallpaperApplied(false)
            prefs.setLastAppliedWallpaperColor(null)
            return false
        }

        if (!isSupported()) {
            return false
        }

        val file = prefs.getSavedOriginalWallpaperFile(context)
        if (!file.exists() || file.length() == 0L) {
            prefs.setHasSavedOriginalWallpaper(false)
            prefs.setWasThemeWallpaperApplied(false)
            prefs.setLastAppliedWallpaperColor(null)
            return false
        }

        var restored = false
        try {
            val bitmap = bitmapProvider.decodeBitmapFromFile(file.absolutePath)
            if (bitmap != null) {
                // Strictly FLAG_SYSTEM: never change lock screen wallpaper!
                wallpaperAdapter.setBitmap(bitmap, WallpaperManager.FLAG_SYSTEM)
                try {
                    bitmap.recycle()
                } catch (_: Throwable) {}
                restored = true
                Log.i(TAG, "Original home screen wallpaper restored successfully")
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error restoring original wallpaper: ${e.message}", e)
        }

        if (restored) {
            try {
                file.delete()
            } catch (_: Throwable) {}
            prefs.setHasSavedOriginalWallpaper(false)
            prefs.setWasThemeWallpaperApplied(false)
            prefs.setLastAppliedWallpaperColor(null)
        }
        return restored
    }

    companion object {
        private const val TAG = "WallpaperThemeManager"

        @Volatile
        private var instance: WallpaperThemeManager? = null

        fun getInstance(context: Context): WallpaperThemeManager {
            return instance ?: synchronized(this) {
                instance ?: WallpaperThemeManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
