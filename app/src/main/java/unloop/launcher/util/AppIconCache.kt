package unloop.launcher.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

object AppIconCache {
    private const val MAX_ENTRIES = 300
    private val cache = object : LinkedHashMap<String, ImageBitmap>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?): Boolean {
            return size > MAX_ENTRIES
        }
    }

    private val failedPackages: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())

    fun get(packageName: String): ImageBitmap? {
        return synchronized(cache) {
            cache[packageName]
        }
    }

    fun put(packageName: String, bitmap: ImageBitmap) {
        synchronized(cache) {
            cache[packageName] = bitmap
        }
    }

    fun remove(packageName: String) {
        synchronized(cache) {
            cache.remove(packageName)
        }
        failedPackages.remove(packageName)
    }

    fun clear() {
        synchronized(cache) {
            cache.clear()
        }
        failedPackages.clear()
    }

    suspend fun loadIcon(context: Context, packageName: String): ImageBitmap? {
        if (failedPackages.contains(packageName)) {
            return null
        }

        get(packageName)?.let { return it }

        return withContext(Dispatchers.IO) {
            try {
                val pm = context.packageManager
                val drawable = pm.getApplicationIcon(packageName)
                val bmp = if (drawable is BitmapDrawable && drawable.bitmap != null) {
                    drawable.bitmap
                } else {
                    val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 96
                    val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 96
                    val b = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(b)
                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                    drawable.draw(canvas)
                    b
                }
                val imageBitmap = bmp?.asImageBitmap()
                if (imageBitmap != null) {
                    put(packageName, imageBitmap)
                } else {
                    failedPackages.add(packageName)
                }
                imageBitmap
            } catch (_: Throwable) {
                failedPackages.add(packageName)
                null
            }
        }
    }
}
