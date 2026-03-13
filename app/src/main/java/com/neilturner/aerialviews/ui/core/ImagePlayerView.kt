package com.neilturner.aerialviews.ui.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.AttributeSet
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.graphics.drawable.toBitmap
import coil3.ImageLoader
import coil3.asDrawable
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.ImageRequest
import com.neilturner.aerialviews.models.enums.AerialMediaSource
import com.neilturner.aerialviews.models.enums.AspectRatio
import com.neilturner.aerialviews.models.enums.PhotoScale
import com.neilturner.aerialviews.models.enums.ProgressBarLocation
import com.neilturner.aerialviews.models.enums.ProgressBarType
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.models.videos.AerialMedia
import com.neilturner.aerialviews.services.InputStreamFetcher
import com.neilturner.aerialviews.ui.core.ImagePlayerHelper.buildGifDecoder
import com.neilturner.aerialviews.ui.core.ImagePlayerHelper.buildOkHttpClient
import com.neilturner.aerialviews.ui.overlays.ProgressBarEvent
import com.neilturner.aerialviews.ui.overlays.ProgressState
import com.neilturner.aerialviews.utils.BitmapHelper
import com.neilturner.aerialviews.utils.FastBlurCompat
import com.neilturner.aerialviews.utils.FirebaseHelper
import com.neilturner.aerialviews.utils.ToastHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.kosert.flowbus.GlobalBus
import timber.log.Timber
import java.io.InputStream
import kotlin.time.Duration.Companion.milliseconds

class ImagePlayerView : FrameLayout {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    private var listener: OnImagePlayerEventListener? = null
    private var finishedRunnable = Runnable { listener?.onImageFinished() }
    private var errorRunnable = Runnable { listener?.onImageError() }
    private val ioScope = CoroutineScope(Dispatchers.IO)
    private val mainScope = CoroutineScope(Dispatchers.Main)

    private var pausedTimestamp: Long = 0
    private var totalDuration: Long = 0
    private var remainingDuration: Long = 0
    private var backgroundJobToken: Long = 0

    private val foregroundImageView =
        AppCompatImageView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }
    private val backgroundImageView =
        AppCompatImageView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            scaleType = ImageView.ScaleType.CENTER_CROP
            visibility = GONE
        }
    private val progressBar =
        GeneralPrefs.progressBarLocation != ProgressBarLocation.DISABLED && GeneralPrefs.progressBarType != ProgressBarType.VIDEOS

    companion object {
        private const val BACKGROUND_BLUR_RADIUS = 32f
        private const val BACKGROUND_BLUR_ALPHA = 0.5f
        private const val LEGACY_BLUR_RADIUS = 12
        private const val LEGACY_DOWNSCALE_FACTOR = 4
    }

    init {
        addView(backgroundImageView)
        addView(foregroundImageView)
    }

    fun release() {
        removeCallbacks(finishedRunnable)
        removeCallbacks(errorRunnable)
        listener = null
    }

    private val imageLoader =
        ImageLoader
            .Builder(context)
            .memoryCache(null)
            // .logger(logger)
            .components {
                add(OkHttpNetworkFetcherFactory(buildOkHttpClient()))
                add(InputStreamFetcher.Factory())
                add(buildGifDecoder())
            }.build()

    fun setImage(media: AerialMedia) {
        ioScope.launch {
            val totalStartTime = System.currentTimeMillis()
            val openSourceStream: () -> InputStream? = {
                ImagePlayerHelper.streamFromMedia(context, media)
            }

            val stream = openSourceStream()
            if (stream == null) {
                loadImage(media.uri)
                return@launch
            }

            runCatching { stream.close() }

            val exifMetadata = BitmapHelper.extractExifMetadata(openSourceStream)

            if (media.source != AerialMediaSource.IMMICH) {
                media.metadata.exif.date = exifMetadata.date
                media.metadata.exif.offset = exifMetadata.offset
                media.metadata.exif.latitude = exifMetadata.latitude
                media.metadata.exif.longitude = exifMetadata.longitude
                media.metadata.exif.description = exifMetadata.description
            }

            Timber.d(
                "ImagePlayerView: Extracted EXIF in ${System.currentTimeMillis() - totalStartTime}ms. source=${media.source} exifDate=%s exifOffset=%s lat=%s lon=%s orientation=%d",
                exifMetadata.date,
                exifMetadata.offset,
                exifMetadata.latitude,
                exifMetadata.longitude,
                exifMetadata.orientation,
            )

            val imageStream = openSourceStream()
            if (imageStream == null) {
                loadImage(media.uri)
                return@launch
            }
            loadImage(imageStream)
        }
    }

    private fun loadImage(data: Any?) {
        try {
            val (targetWidth, targetHeight) = resolveTargetSize()
            val request =
                ImageRequest
                    .Builder(context)
                    .data(data)
                    .size(targetWidth, targetHeight)
                    .target(
                        onStart = {
                            //resetImageTransforms()
                        },
                        onSuccess = { image ->
                            val drawable = image.asDrawable(resources)
                            foregroundImageView.setImageDrawable(drawable)
                            updateBackgroundImage(drawable)
                        },
                    ).listener(
                        onSuccess = { _, result ->
                            setScaleMode(result.image.width, result.image.height)
                            setupFinishedRunnable()
                        },
                        onError = { _, result ->
                            handleImageError(result.throwable)
                        },
                    )
                    .build()
            imageLoader.enqueue(request)
        } catch (ex: Exception) {
            Timber.e(ex, "Exception while trying to load image: ${ex.message}")

            // Show toast if preference is enabled
            if (GeneralPrefs.showMediaErrorToasts) {
                mainScope.launch {
                    val errorMessage = ex.localizedMessage ?: "Photo loading error occurred"
                    ToastHelper.show(context, errorMessage)
                }
            }

            listener?.onImageError()
        }
    }

    private fun resolveTargetSize(): Pair<Int, Int> {
        val width = if (this.width > 0) this.width else resources.displayMetrics.widthPixels
        val height = if (this.height > 0) this.height else resources.displayMetrics.heightPixels
        return Pair(width, height)
    }

    private fun updateBackgroundImage(drawable: Drawable?) {
        if (drawable != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val backgroundDrawable = drawable.constantState?.newDrawable()?.mutate() ?: drawable
                backgroundImageView.setImageDrawable(backgroundDrawable)
                applyBackgroundBlur()
                backgroundImageView.alpha = BACKGROUND_BLUR_ALPHA
                if (backgroundImageView.visibility != VISIBLE) {
                    backgroundImageView.visibility = VISIBLE
                }
            } else {
                applyLegacyBackgroundBlur(drawable)
            }
        } else {
            backgroundJobToken++
            backgroundImageView.setImageDrawable(null)
            backgroundImageView.visibility = GONE
            clearBackgroundBlur()
        }
    }

    private fun applyBackgroundBlur() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            backgroundImageView.setRenderEffect(
                RenderEffect.createBlurEffect(
                    BACKGROUND_BLUR_RADIUS,
                    BACKGROUND_BLUR_RADIUS,
                    Shader.TileMode.CLAMP,
                ),
            )
        }
    }

    private fun clearBackgroundBlur() {
        // No-op: RenderEffect is only used on Android 12+ and stays enabled for the session.
    }

    private fun applyLegacyBackgroundBlur(drawable: Drawable) {
        val token = ++backgroundJobToken
        val (targetWidth, targetHeight) = resolveTargetSize()
        val downscaledWidth = maxOf(1, targetWidth / LEGACY_DOWNSCALE_FACTOR)
        val downscaledHeight = maxOf(1, targetHeight / LEGACY_DOWNSCALE_FACTOR)

        ioScope.launch {
            val (sourceBitmap, recycleSource) = drawableToSoftwareBitmap(drawable, downscaledWidth, downscaledHeight)
            // Always blur a mutable copy to avoid mutating shared bitmaps.
            val mutable = sourceBitmap.copy(Bitmap.Config.ARGB_8888, true)
            if (mutable !== sourceBitmap && recycleSource) {
                sourceBitmap.recycle()
            }

            FastBlurCompat.applyBlur(mutable, LEGACY_BLUR_RADIUS)

            mainScope.launch {
                if (token != backgroundJobToken) {
                    mutable.recycle()
                    return@launch
                }
                backgroundImageView.setImageBitmap(mutable)
                backgroundImageView.alpha = BACKGROUND_BLUR_ALPHA
                if (backgroundImageView.visibility != VISIBLE) {
                    backgroundImageView.visibility = VISIBLE
                }
            }
        }
    }

    private fun drawableToSoftwareBitmap(
        drawable: Drawable,
        width: Int,
        height: Int,
    ): Pair<Bitmap, Boolean> {
        return when (drawable) {
            is BitmapDrawable -> {
                val bitmap = drawable.bitmap
                if (bitmap.config == Bitmap.Config.HARDWARE) {
                    val copied = bitmap.copy(Bitmap.Config.ARGB_8888, false)
                    Pair(copied, true)
                } else if (bitmap.width != width || bitmap.height != height) {
                    val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
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
    }

    private fun handleImageError(throwable: Throwable) {
        Timber.e(throwable, "Exception while loading image: ${throwable.message}")
        FirebaseHelper.crashlyticsException(throwable)

        if (GeneralPrefs.showMediaErrorToasts) {
            mainScope.launch {
                val errorMessage = throwable.localizedMessage ?: "Photo loading error occurred"
                ToastHelper.show(context, errorMessage)
            }
        }

        onPlayerError()
    }

    fun stop() {
        removeCallbacks(finishedRunnable)
        foregroundImageView.setImageBitmap(null)
        pausedTimestamp = 0
        remainingDuration = 0
        backgroundJobToken++
        backgroundImageView.setImageBitmap(null)
        backgroundImageView.visibility = GONE
        clearBackgroundBlur()
    }

    fun pauseTimer() {
        pausedTimestamp = System.currentTimeMillis()
        removeCallbacks(finishedRunnable)
    }

    fun resumeTimer(pauseDuration: Long) {
        if (pausedTimestamp > 0) {
            remainingDuration = maxOf(0, remainingDuration - pauseDuration)
            if (remainingDuration > 0) {
                postDelayed(finishedRunnable, remainingDuration)
            } else {
                // If time has expired, finish immediately
                listener?.onImageFinished()
            }
            pausedTimestamp = 0
        }
    }

    private fun setScaleMode(
        width: Int,
        height: Int,
    ) {
        val aspect = AspectRatio.fromDimensions(width, height)
        Timber.i("Aspect ratio: $aspect")
        foregroundImageView.scaleType =
            when (aspect) {
                AspectRatio.SQUARE -> {
                    getScaleType(GeneralPrefs.photoScalePortrait)
                }

                AspectRatio.PORTRAIT -> {
                    getScaleType(GeneralPrefs.photoScalePortrait)
                }

                AspectRatio.LANDSCAPE -> {
                    getScaleType(GeneralPrefs.photoScaleLandscape)
                }
            }
    }

    private fun getScaleType(scale: PhotoScale?): ImageView.ScaleType =
        try {
            ImageView.ScaleType.valueOf(scale.toString())
        } catch (e: Exception) {
            Timber.e(e)
            ImageView.ScaleType.valueOf(PhotoScale.CENTER_CROP.toString())
        }

    private fun setupFinishedRunnable() {
        removeCallbacks(finishedRunnable)
        listener?.onImagePrepared()

        val duration = GeneralPrefs.slideshowSpeed.toLong() * 1000
        val fadeDuration = GeneralPrefs.mediaFadeOutDuration.toLong()
        val durationMinusFade = duration - fadeDuration

        totalDuration = duration
        remainingDuration = durationMinusFade

        Timber.i("Delay: ${durationMinusFade.milliseconds}")
        if (progressBar) GlobalBus.post(ProgressBarEvent(ProgressState.START, 0, duration))
        postDelayed(finishedRunnable, durationMinusFade)
    }

    private fun onPlayerError() {
        removeCallbacks(finishedRunnable)
        postDelayed(errorRunnable, ScreenController.ERROR_DELAY)
    }

    fun setOnPlayerListener(listener: ScreenController) {
        this.listener = listener
    }

    interface OnImagePlayerEventListener {
        fun onImageFinished()

        fun onImageError()

        fun onImagePrepared()
    }
}
