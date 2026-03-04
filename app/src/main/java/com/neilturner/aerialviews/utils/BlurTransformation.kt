package com.neilturner.aerialviews.utils

import android.graphics.Bitmap
import coil3.size.Size
import coil3.transform.Transformation
import androidx.core.graphics.scale
import timber.log.Timber

/**
 * A Coil3 [Transformation] that applies a blur to a bitmap.
 *
 * On API 31+ (Android 12+) uses [android.graphics.RenderEffect] for GPU-accelerated blur.
 * On API 30 and below, uses [FastBlurCompat] (CPU-based box blur) as a fallback.
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
) : Transformation() {
    override val cacheKey: String = "${BlurTransformation::class.java.name}-$radius-$downscaleFactor"

    override suspend fun transform(
        input: Bitmap,
        size: Size,
    ): Bitmap {
        Timber.d("BlurTransformation: Starting transform. Size: ${input.width}x${input.height}")
        val scaledWidth = maxOf(1, (input.width / downscaleFactor).toInt())
        val scaledHeight = maxOf(1, (input.height / downscaleFactor).toInt())

        // Downscale for performance (and stronger-looking blur)
        val downscaled = input.scale(scaledWidth, scaledHeight)

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
        
        Timber.d("BlurTransformation: Finished transform")
        return workBitmap
    }
}
