package com.neilturner.aerialviews.utils

import android.graphics.Bitmap
import androidx.core.graphics.scale
import coil3.size.Size
import coil3.transform.Transformation
import timber.log.Timber

/**
 * A Coil3 [Transformation] that applies a blur to a bitmap.
 *
 * This transformation is used for software blur on pre-Android 12 devices.
 * On Android 12+ the app applies blur with [android.graphics.RenderEffect] at the View layer.
 *
 * For performance, the image is first downscaled by [downscaleFactor] before blurring,
 * then upscaled back. This also produces a stronger blur effect.
 *
 * @param radius The blur radius. Default is 25.
 * @param downscaleFactor Divide dimensions by this amount before blurring. Default is 4.
 */
class BlurTransformation(
    private val radius: Int = 25,
    private val downscaleFactor: Float = 4f,
    private val useBilinearFiltering: Boolean = true,
) : Transformation() {
    override val cacheKey: String = "${BlurTransformation::class.java.name}-$radius-$downscaleFactor-$useBilinearFiltering"

    override suspend fun transform(
        input: Bitmap,
        size: Size,
    ): Bitmap {
        Timber.d("BlurTransformation: Starting transform. Size: ${input.width}x${input.height}")
        val scaledWidth = maxOf(1, (input.width / downscaleFactor).toInt())
        val scaledHeight = maxOf(1, (input.height / downscaleFactor).toInt())

        // Downscale for performance (and stronger-looking blur)
        val downscaled = input.scale(scaledWidth, scaledHeight, useBilinearFiltering)

        // Convert to ARGB_8888 if needed for pixel manipulation
        val workBitmap =
            if (downscaled.config == Bitmap.Config.ARGB_8888) {
                downscaled
            } else {
                downscaled.copy(Bitmap.Config.ARGB_8888, true)
            }
        if (workBitmap !== downscaled) downscaled.recycle()

        Timber.d("BlurTransformation: Using FastBlurCompat")
        FastBlurCompat.applyBlur(workBitmap, radius)

        val outputBitmap =
            if (workBitmap.width == input.width && workBitmap.height == input.height) {
                workBitmap
            } else {
                val upscaled = workBitmap.scale(input.width, input.height, useBilinearFiltering)
                workBitmap.recycle()
                upscaled
            }

        Timber.d("BlurTransformation: Finished transform")
        return outputBitmap
    }
}
