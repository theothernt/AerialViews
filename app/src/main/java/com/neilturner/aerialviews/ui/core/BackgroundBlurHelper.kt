package com.neilturner.aerialviews.ui.core

import android.graphics.Bitmap
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.View.GONE
import android.view.View.VISIBLE
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.scale
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.utils.FastBlurCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Manages the blurred background image shown behind the foreground photo.
 *
 * On API >= S the blur is applied via [RenderEffect] on the UI thread.
 * On older APIs a software fast-blur is computed on [ioScope] and posted back to [mainScope].
 *
 * When the background is ready (or cleared), [onReady] is called with the current token so
 * [ImagePlayerView] can coordinate the two-flow sync gate.
 */
class BackgroundBlurHelper(
    private val backgroundImageView: AppCompatImageView,
    private val ioScope: CoroutineScope,
    private val mainScope: CoroutineScope,
    private val resolveTargetSize: () -> Pair<Int, Int>,
    private val onReady: (token: Long) -> Unit,
) {
    private var backgroundJobToken: Long = 0

    /** The token for the most-recently started background job. */
    val currentToken: Long get() = backgroundJobToken

    companion object {
        private const val BASE_BACKGROUND_BLUR_RADIUS = 32f
        private const val BASE_LEGACY_BLUR_RADIUS = 12
        private const val LEGACY_DOWNSCALE_FACTOR = 6
        private const val LEGACY_MAX_BLUR_DIM = 480
        private const val BLUR_INTENSITY_DEFAULT = 50
        private const val BLUR_INTENSITY_MIN = 5
        private const val BLUR_INTENSITY_MAX = 100
    }

    /** Cancels any in-flight blur job and hides the background view. */
    fun cancel() {
        backgroundJobToken++
        backgroundImageView.setImageBitmap(null)
        backgroundImageView.setRenderEffect(null)
        backgroundImageView.visibility = GONE
    }

    /** Updates the background with a blurred copy of [drawable], or hides it if blur is disabled. */
    fun update(drawable: Drawable?) {
        val token = ++backgroundJobToken

        if (drawable != null && GeneralPrefs.photoBackgroundBlurEnabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val backgroundDrawable = drawable.constantState?.newDrawable()?.mutate() ?: drawable
                backgroundImageView.setImageDrawable(backgroundDrawable)
                applyBackgroundBlur()
                backgroundImageView.alpha = resolveBackgroundBlurAlpha()
                if (backgroundImageView.visibility != VISIBLE) {
                    backgroundImageView.visibility = VISIBLE
                }
                onReady(token)
            } else {
                applyLegacyBackgroundBlur(drawable, token)
            }
        } else {
            backgroundImageView.setImageDrawable(null)
            backgroundImageView.setRenderEffect(null)
            backgroundImageView.visibility = GONE
            onReady(token)
        }
    }

    private fun applyBackgroundBlur() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val radius = resolveBackgroundBlurRadius()
            backgroundImageView.setRenderEffect(
                RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP),
            )
        }
    }

    private fun applyLegacyBackgroundBlur(
        drawable: Drawable,
        token: Long,
    ) {
        val (downscaledWidth, downscaledHeight) = resolveLegacyBlurTargetSize()

        ioScope.launch {
            val (sourceBitmap, recycleSource) = drawableToSoftwareBitmap(drawable, downscaledWidth, downscaledHeight)
            // Always blur a mutable copy to avoid mutating shared bitmaps.
            val mutable = sourceBitmap.copy(Bitmap.Config.ARGB_8888, true)
            if (mutable !== sourceBitmap && recycleSource) {
                sourceBitmap.recycle()
            }

            FastBlurCompat.applyBlur(mutable, resolveLegacyBlurRadius())

            mainScope.launch {
                if (token != backgroundJobToken) {
                    mutable.recycle()
                    return@launch
                }
                backgroundImageView.setImageBitmap(mutable)
                backgroundImageView.alpha = resolveBackgroundBlurAlpha()
                if (backgroundImageView.visibility != VISIBLE) {
                    backgroundImageView.visibility = VISIBLE
                }
                onReady(token)
            }
        }
    }

    private fun drawableToSoftwareBitmap(
        drawable: Drawable,
        width: Int,
        height: Int,
    ): Pair<Bitmap, Boolean> =
        when (drawable) {
            is BitmapDrawable -> {
                val bitmap = drawable.bitmap
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                    bitmap.config == Bitmap.Config.HARDWARE
                ) {
                    val copied = bitmap.copy(Bitmap.Config.ARGB_8888, false)
                    Pair(copied, true)
                } else if (bitmap.width != width || bitmap.height != height) {
                    val scaled = bitmap.scale(width, height)
                    Pair(scaled, true)
                } else {
                    Pair(bitmap, false)
                }
            }

            else -> {
                val bitmap = drawable.toBitmap(width = width, height = height, config = Bitmap.Config.ARGB_8888)
                Pair(bitmap, true)
            }
        }

    private fun resolveLegacyBlurTargetSize(): Pair<Int, Int> {
        val (targetWidth, targetHeight) = resolveTargetSize()
        var downscaledWidth = maxOf(1, targetWidth / LEGACY_DOWNSCALE_FACTOR)
        var downscaledHeight = maxOf(1, targetHeight / LEGACY_DOWNSCALE_FACTOR)

        val maxDim = maxOf(downscaledWidth, downscaledHeight)
        if (maxDim > LEGACY_MAX_BLUR_DIM) {
            val scale = LEGACY_MAX_BLUR_DIM.toFloat() / maxDim.toFloat()
            downscaledWidth = maxOf(1, (downscaledWidth * scale).toInt())
            downscaledHeight = maxOf(1, (downscaledHeight * scale).toInt())
        }

        return Pair(downscaledWidth, downscaledHeight)
    }

    private fun resolveBlurIntensityFactor(): Float {
        val rawValue = GeneralPrefs.photoBackgroundBlurIntensity.toIntOrNull() ?: BLUR_INTENSITY_DEFAULT
        val clamped = rawValue.coerceIn(BLUR_INTENSITY_MIN, BLUR_INTENSITY_MAX)
        return clamped / BLUR_INTENSITY_DEFAULT.toFloat()
    }

    private fun resolveBackgroundBlurAlpha(): Float {
        val rawValue = GeneralPrefs.photoBackgroundBlurOpacity.toIntOrNull() ?: 30
        val clamped = rawValue.coerceIn(0, 100)
        return clamped / 100f
    }

    private fun resolveBackgroundBlurRadius(): Float = BASE_BACKGROUND_BLUR_RADIUS * resolveBlurIntensityFactor()

    private fun resolveLegacyBlurRadius(): Int {
        val radius = BASE_LEGACY_BLUR_RADIUS * resolveBlurIntensityFactor()
        return maxOf(1, radius.roundToInt())
    }
}
