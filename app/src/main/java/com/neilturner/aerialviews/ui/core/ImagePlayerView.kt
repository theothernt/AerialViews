package com.neilturner.aerialviews.ui.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.util.AttributeSet
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatImageView
import androidx.exifinterface.media.ExifInterface
import coil3.EventListener
import coil3.ImageLoader
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.request.transformations
import coil3.target.ImageViewTarget
import com.neilturner.aerialviews.models.enums.AerialMediaSource
import com.neilturner.aerialviews.models.enums.AspectRatio
import com.neilturner.aerialviews.models.enums.PhotoScale
import com.neilturner.aerialviews.models.enums.ProgressBarLocation
import com.neilturner.aerialviews.models.enums.ProgressBarType
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.models.videos.AerialMedia
import com.neilturner.aerialviews.services.InputStreamFetcher
import com.neilturner.aerialviews.ui.core.ImagePlayerHelper.buildGifDecoder
import com.neilturner.aerialviews.ui.overlays.ProgressBarEvent
import com.neilturner.aerialviews.ui.overlays.ProgressState
import com.neilturner.aerialviews.utils.BitmapHelper
import com.neilturner.aerialviews.utils.BlurTransformation
import com.neilturner.aerialviews.utils.FirebaseHelper
import com.neilturner.aerialviews.utils.ToastHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private var target = ImageViewTarget(foregroundImageView)
    private var currentOrientation: Int = ExifInterface.ORIENTATION_UNDEFINED

    private val progressBar =
        GeneralPrefs.progressBarLocation != ProgressBarLocation.DISABLED && GeneralPrefs.progressBarType != ProgressBarType.VIDEOS

    companion object {
        // Tuned to visually approximate the current software blur on TV devices.
        private const val RENDER_EFFECT_BLUR_RADIUS = 32f
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

    private val eventLister =
        object : EventListener() {
            override fun onSuccess(
                request: ImageRequest,
                result: SuccessResult,
            ) {
                super.onSuccess(request, result)
                if (result.request.target == target) {
                    setScaleMode(result.image.width, result.image.height, currentOrientation)
                    setupFinishedRunnable()
                }
            }

            override fun onError(
                request: ImageRequest,
                result: ErrorResult,
            ) {
                super.onError(request, result)
                Timber.e(result.throwable, "Exception while loading image: ${result.throwable.message}")
                FirebaseHelper.crashlyticsException(result.throwable)

                // Show toast if preference is enabled
                if (GeneralPrefs.showMediaErrorToasts) {
                    mainScope.launch {
                        val errorMessage = result.throwable.localizedMessage ?: "Photo loading error occurred"
                        ToastHelper.show(context, errorMessage)
                    }
                }

                onPlayerError()
            }
        }

    private val imageLoader =
        ImageLoader
            .Builder(context)
            .memoryCache(null)
            // .logger(logger)
            .eventListener(eventLister)
            .components {
                // add(OkHttpNetworkFetcherFactory(buildOkHttpClient()))
                add(InputStreamFetcher.Factory()) // GIF
                add(buildGifDecoder()) // GIF
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

            if (isGifStream(stream)) {
                loadImage(stream)
                return@launch
            }
            runCatching { stream.close() }

            val targetWidth = if (this@ImagePlayerView.width > 0) this@ImagePlayerView.width else resources.displayMetrics.widthPixels
            val targetHeight = if (this@ImagePlayerView.height > 0) this@ImagePlayerView.height else resources.displayMetrics.heightPixels

            val bitmapResult = BitmapHelper.loadResizedImageBytes(openSourceStream, targetWidth, targetHeight)
            if (bitmapResult == null) {
                loadImage(media.uri)
                return@launch
            }

            if (media.source != AerialMediaSource.IMMICH) {
                media.metadata.exif.date = bitmapResult.metadata.date
                media.metadata.exif.offset = bitmapResult.metadata.offset
                media.metadata.exif.latitude = bitmapResult.metadata.latitude
                media.metadata.exif.longitude = bitmapResult.metadata.longitude
                media.metadata.exif.description = bitmapResult.metadata.description
            }

            Timber.d(
                "ImagePlayerView: Loaded bitmap for display in ${System.currentTimeMillis() - totalStartTime}ms. source=${media.source} exifDate=%s exifOffset=%s lat=%s lon=%s orientation=%d",
                bitmapResult.metadata.date,
                bitmapResult.metadata.offset,
                bitmapResult.metadata.latitude,
                bitmapResult.metadata.longitude,
                bitmapResult.metadata.orientation,
            )

            // Load blurred background if enabled
            if (GeneralPrefs.photoBackgroundBlurEnabled) {
                loadBlurredBackground(bitmapResult.bitmap, bitmapResult.metadata.orientation)
            } else {
                clearBlurredBackground()
            }

            loadImage(bitmapResult.bitmap, bitmapResult.metadata.orientation)
        }
    }

    private fun isGifStream(stream: InputStream): Boolean {
        return try {
            stream.mark(6)
            val header = ByteArray(6)
            val read = stream.read(header)
            stream.reset()
            if (read < 6) return false
            header[0] == 'G'.code.toByte() &&
                header[1] == 'I'.code.toByte() &&
                header[2] == 'F'.code.toByte() &&
                header[3] == '8'.code.toByte() &&
                (header[4] == '7'.code.toByte() || header[4] == '9'.code.toByte()) &&
                header[5] == 'a'.code.toByte()
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun loadImage(
        data: Any?,
        orientation: Int = ExifInterface.ORIENTATION_UNDEFINED,
    ) {
        try {
            currentOrientation = orientation
            val request =
                ImageRequest
                    .Builder(context)
                    .data(data)
                    .size(this.width, this.height)
                    .target(target)
                    .build()
            imageLoader.execute(request)
            withContext(Dispatchers.Main) {
                applyExifRotation(foregroundImageView, orientation)
            }
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

    private suspend fun loadBlurredBackground(
        bitmap: Bitmap,
        orientation: Int,
    ) {
        Timber.d("loadBlurredBackground: Starting request with blur opacity ${GeneralPrefs.photoBackgroundBlurOpacity}")
        try {
            val opacity = (GeneralPrefs.photoBackgroundBlurOpacity.toIntOrNull() ?: 100) / 100f
            val isApi31Plus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            val requestData =
                if (isApi31Plus) {
                    bitmap
                } else {
                    bitmap.toSoftwareBitmap()
                }

            withContext(Dispatchers.Main) {
                if (backgroundImageView.visibility != VISIBLE) {
                    backgroundImageView.alpha = 0f
                    backgroundImageView.visibility = VISIBLE
                }
            }

            val requestBuilder =
                ImageRequest
                    .Builder(context)
                    .data(requestData)
                    .size(this.width, this.height)
                    .target(ImageViewTarget(backgroundImageView))
                    .listener(
                        onError = { _, result ->
                            Timber.e("loadBlurredBackground: Coil request failed: ${result.throwable.message}")
                            clearRenderEffectIfSupported()
                        },
                        onSuccess = { _, _ ->
                            Timber.d("loadBlurredBackground: Coil request succeeded")
                            applyExifRotation(backgroundImageView, orientation)
                            if (isApi31Plus) {
                                applyRenderEffectBlur()
                            }
                        },
                    )

            if (isApi31Plus) {
                clearRenderEffectIfSupported()
            } else {
                requestBuilder
                    .allowHardware(false)
                    .transformations(listOf(BlurTransformation(useBilinearFiltering = GeneralPrefs.photoBilinearFiltering)))
            }

            val request = requestBuilder.build()
            imageLoader.execute(request)
            withContext(Dispatchers.Main) {
                backgroundImageView.alpha = opacity
                Timber.d("loadBlurredBackground: Updated background view alpha to $opacity")
            }
        } catch (ex: Exception) {
            Timber.e(ex, "Exception while loading blurred background: ${ex.message}")
        }
    }

    private fun Bitmap.toSoftwareBitmap(): Bitmap =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && config == Bitmap.Config.HARDWARE) {
            copy(Bitmap.Config.ARGB_8888, false)
        } else {
            this
        }

    private suspend fun clearBlurredBackground() {
        withContext(Dispatchers.Main) {
            backgroundImageView.setImageBitmap(null)
            backgroundImageView.visibility = GONE
            clearRenderEffectIfSupported()
        }
    }

    private fun applyRenderEffectBlur() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            backgroundImageView.setRenderEffect(
                RenderEffect.createBlurEffect(
                    RENDER_EFFECT_BLUR_RADIUS,
                    RENDER_EFFECT_BLUR_RADIUS,
                    Shader.TileMode.CLAMP,
                ),
            )
        }
    }

    private fun clearRenderEffectIfSupported() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            backgroundImageView.setRenderEffect(null)
        }
    }

    private fun applyExifRotation(
        view: ImageView,
        orientation: Int,
    ) {
        val startTime = System.currentTimeMillis()
        view.rotation = 0f
        view.scaleX = 1f
        view.scaleY = 1f

        val isSwapped =
            orientation == ExifInterface.ORIENTATION_ROTATE_90 ||
                orientation == ExifInterface.ORIENTATION_ROTATE_270 ||
                orientation == ExifInterface.ORIENTATION_TRANSPOSE ||
                orientation == ExifInterface.ORIENTATION_TRANSVERSE

        val rotationAngle =
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90,
                ExifInterface.ORIENTATION_TRANSPOSE,
                ExifInterface.ORIENTATION_TRANSVERSE,
                -> {
                    90f
                }

                ExifInterface.ORIENTATION_ROTATE_180,
                ExifInterface.ORIENTATION_FLIP_VERTICAL,
                -> {
                    180f
                }

                ExifInterface.ORIENTATION_ROTATE_270 -> {
                    270f
                }

                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> {
                    view.scaleX = -1f
                    0f
                }

                else -> {
                    0f
                }
            }

        view.rotation = rotationAngle

        if (isSwapped) {
            val parentWidth = if (width > 0) width.toFloat() else resources.displayMetrics.widthPixels.toFloat()
            val parentHeight = if (height > 0) height.toFloat() else resources.displayMetrics.heightPixels.toFloat()
            if (parentWidth > 0 && parentHeight > 0) {
                // If the view is rotated 90/270, we need to scale it to cover the parent gaps.
                val scale = maxOf(parentWidth / parentHeight, parentHeight / parentWidth)
                if (view == backgroundImageView || view.scaleType == ImageView.ScaleType.CENTER_CROP) {
                    view.scaleX *= scale
                    view.scaleY = scale
                }
            }
        }
        Timber.d(
            "ImagePlayerView: Applied rotation ($orientation -> ${view.rotation}deg) in ${System.currentTimeMillis() - startTime}ms. View=${if (view == foregroundImageView) "Foreground" else "Background"}",
        )
    }

    fun stop() {
        removeCallbacks(finishedRunnable)
        foregroundImageView.setImageBitmap(null)
        foregroundImageView.rotation = 0f
        foregroundImageView.scaleX = 1f
        foregroundImageView.scaleY = 1f
        pausedTimestamp = 0
        remainingDuration = 0
        currentOrientation = ExifInterface.ORIENTATION_UNDEFINED
        backgroundImageView.setImageBitmap(null)
        backgroundImageView.rotation = 0f
        backgroundImageView.scaleX = 1f
        backgroundImageView.scaleY = 1f
        backgroundImageView.visibility = GONE
        clearRenderEffectIfSupported()
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
        orientation: Int,
    ) {
        val isSwapped =
            orientation == ExifInterface.ORIENTATION_ROTATE_90 ||
                orientation == ExifInterface.ORIENTATION_ROTATE_270 ||
                orientation == ExifInterface.ORIENTATION_TRANSPOSE ||
                orientation == ExifInterface.ORIENTATION_TRANSVERSE
        val finalWidth = if (isSwapped) height else width
        val finalHeight = if (isSwapped) width else height

        val aspect = AspectRatio.fromDimensions(finalWidth, finalHeight)
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
        applyExifRotation(foregroundImageView, orientation)
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
