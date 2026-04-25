package com.neilturner.aerialviews.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream

/**
 * Stores a single JPEG snapshot of a recently-shown slideshow photo so the next
 * screensaver start can display it behind the loading UI instead of a black screen.
 *
 * Intentionally keeps exactly one file (overwritten each time) — across sessions the
 * saved photo naturally changes because different sessions capture different slots.
 * Writes are deliberately infrequent (the caller decides cadence) to be gentle on
 * device flash.
 */
object SplashCache {
    private const val FILE_NAME = "loading_splash.jpg"
    private const val JPEG_QUALITY = 80

    private fun file(context: Context): File = File(context.cacheDir, FILE_NAME)

    fun save(
        context: Context,
        bitmap: Bitmap,
    ) {
        if (bitmap.isRecycled) return
        try {
            FileOutputStream(file(context)).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
            Timber.i("SplashCache: saved (${bitmap.width}x${bitmap.height})")
        } catch (e: Exception) {
            Timber.w(e, "SplashCache: save failed")
        }
    }

    fun loadBitmap(context: Context): Bitmap? {
        val f = file(context)
        if (!f.exists() || f.length() == 0L) return null
        return try {
            BitmapFactory.decodeFile(f.absolutePath)
        } catch (e: Exception) {
            Timber.w(e, "SplashCache: load failed")
            null
        }
    }
}
