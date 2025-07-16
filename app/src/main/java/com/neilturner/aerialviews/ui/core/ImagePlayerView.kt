package com.neilturner.aerialviews.ui.core

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatImageView
import coil3.EventListener
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.target.ImageViewTarget
import com.neilturner.aerialviews.models.enums.AerialMediaSource
import com.neilturner.aerialviews.models.enums.PhotoScale
import com.neilturner.aerialviews.models.enums.ProgressBarLocation
import com.neilturner.aerialviews.models.enums.ProgressBarType
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.models.videos.AerialMedia
import com.neilturner.aerialviews.services.InputStreamFetcher
import com.neilturner.aerialviews.ui.core.ImagePlayerHelper.buildGifDecoder
import com.neilturner.aerialviews.ui.core.ImagePlayerHelper.buildOkHttpClient
import com.neilturner.aerialviews.ui.core.ImagePlayerHelper.logger
import com.neilturner.aerialviews.ui.overlays.ProgressBarEvent
import com.neilturner.aerialviews.ui.overlays.ProgressState
import com.neilturner.aerialviews.utils.FirebaseHelper
import com.neilturner.aerialviews.utils.ToastHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.kosert.flowbus.GlobalBus
import timber.log.Timber
import kotlin.time.Duration.Companion.milliseconds

class ImagePlayerView : AppCompatImageView {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    private var listener: OnImagePlayerEventListener? = null
    private var finishedRunnable = Runnable { listener?.onImageFinished() }
    private var errorRunnable = Runnable { listener?.onImageError() }
    private val ioScope = CoroutineScope(Dispatchers.IO)
    private val mainScope = CoroutineScope(Dispatchers.Main)
    private var target = ImageViewTarget(this)
    private var backgroundImageView: ImageView? = null

    private val progressBar =
        GeneralPrefs.progressBarLocation != ProgressBarLocation.DISABLED && GeneralPrefs.progressBarType != ProgressBarType.VIDEOS

    init {
        val scaleType =
            try {
                ScaleType.valueOf(GeneralPrefs.photoScale.toString())
            } catch (e: Exception) {
                Timber.e(e)
                GeneralPrefs.photoScale = PhotoScale.CENTER_CROP
                ScaleType.valueOf(PhotoScale.CENTER_CROP.toString())
            }
        this.scaleType = scaleType
    }

    fun setBackgroundImageView(backgroundImageView: ImageView) {
        this.backgroundImageView = backgroundImageView
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
                checkAndApplyBlurBackground()
                setupFinishedRunnable()
            }

            override fun onError(
                request: ImageRequest,
                result: ErrorResult,
            ) {
                super.onError(request, result)
                Timber.e(result.throwable, "Exception while loading image: ${result.throwable.message}")
                FirebaseHelper.logExceptionIfRecent(result.throwable)

                // Show toast if preference is enabled
                if (GeneralPrefs.showMediaErrorToasts) {
                    mainScope.launch {
                        val errorMessage = result.throwable.localizedMessage ?: "Media loading error occurred"
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
            .logger(logger)
            .eventListener(eventLister)
            .components {
                add(OkHttpNetworkFetcherFactory(buildOkHttpClient()))
                add(InputStreamFetcher.Factory())
                add(buildGifDecoder())
            }.build()

    fun setImage(media: AerialMedia) {
        ioScope.launch {
            when (media.source) {
                AerialMediaSource.SAMBA -> {
                    val stream = ImagePlayerHelper.streamFromSambaFile(media.uri)
                    loadImage(stream)
                }
                AerialMediaSource.WEBDAV -> {
                    val stream = ImagePlayerHelper.streamFromWebDavFile(media.uri)
                    loadImage(stream)
                }
                else -> {
                    loadImage(media.uri)
                }
            }
        }
    }

    private fun checkAndApplyBlurBackground() {
        // Get the drawable from the ImageView target after it's been set
        val drawable = this.drawable
        if (drawable == null) {
            Timber.d("No drawable found, hiding background")
            backgroundImageView?.visibility = GONE
            return
        }

        val imageWidth = drawable.intrinsicWidth
        val imageHeight = drawable.intrinsicHeight

        val screenWidth = this.width
        val screenHeight = this.height

        Timber.d("Image dimensions: ${imageWidth}x${imageHeight}, Screen dimensions: ${screenWidth}x${screenHeight}")

        if (screenWidth <= 0 || screenHeight <= 0 || imageWidth <= 0 || imageHeight <= 0) {
            Timber.d("Invalid dimensions, hiding background")
            backgroundImageView?.visibility = GONE
            return
        }

        // Calculate aspect ratios
        val imageAspectRatio = imageWidth.toFloat() / imageHeight.toFloat()
        val screenAspectRatio = screenWidth.toFloat() / screenHeight.toFloat()
        val aspectRatioDiff = kotlin.math.abs(imageAspectRatio - screenAspectRatio)

        Timber.d("Image aspect ratio: $imageAspectRatio, Screen aspect ratio: $screenAspectRatio, Difference: $aspectRatioDiff")

        // Check if image will have letterboxing or pillarboxing
        val needsBlurBackground = when (this.scaleType) {
            ScaleType.FIT_CENTER, ScaleType.FIT_START, ScaleType.FIT_END -> {
                // For FIT scale types, blur is needed if aspect ratios don't match
                aspectRatioDiff > 0.01f
            }
            ScaleType.CENTER_INSIDE -> {
                // For CENTER_INSIDE, check if image is smaller than screen or has different aspect ratio
                (imageWidth < screenWidth || imageHeight < screenHeight) || aspectRatioDiff > 0.01f
            }
            else -> false // CENTER_CROP, MATRIX, etc. don't need blur background
        }

        Timber.d("Scale type: ${this.scaleType}, Needs blur background: $needsBlurBackground")

        if (needsBlurBackground && backgroundImageView != null) {
            Timber.d("Showing background for image that needs it")
            // Use the drawable from the already loaded image instead of reloading
            loadBlurredBackgroundFromDrawable(drawable)
        } else {
            Timber.d("Hiding background - not needed or backgroundImageView is null")
            backgroundImageView?.visibility = GONE
        }
    }

    private suspend fun loadImage(data: Any?) {
        try {
            val request =
                ImageRequest
                    .Builder(context)
                    .data(data)
                    .size(this.width, this.height)
                    .target(target)
                    .build()
            imageLoader.execute(request)
        } catch (ex: Exception) {
            Timber.e(ex, "Exception while trying to load image: ${ex.message}")

            // Show toast if preference is enabled
            if (GeneralPrefs.showMediaErrorToasts) {
                mainScope.launch {
                    val errorMessage = ex.localizedMessage ?: "Media loading error occurred"
                    ToastHelper.show(context, errorMessage)
                }
            }

            listener?.onImageError()
        }
    }

    private fun loadBlurredBackgroundFromDrawable(sourceDrawable: Drawable) {
        try {
            backgroundImageView?.let { bgImageView ->
                Timber.d("Loading background from existing drawable")

                // Set the drawable directly to the background ImageView
                bgImageView.setImageDrawable(sourceDrawable)

                // Apply blur and darken effect to the ImageView
                bgImageView.apply {
                    // Show the background
                    visibility = VISIBLE

                    // Apply blur effect using RenderEffect (Android 12+)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        setRenderEffect(
                            android.graphics.RenderEffect.createBlurEffect(
                                25f, 25f,
                                android.graphics.Shader.TileMode.CLAMP
                            )
                        )
                    }

                    // Apply darkening overlay
                    alpha = 0.7f // Make it 30% darker

                    Timber.d("Background image loaded and effects applied")
                }
            } ?: run {
                Timber.w("backgroundImageView is null, cannot load background")
            }
        } catch (ex: Exception) {
            Timber.w(ex, "Failed to load blurred background: ${ex.message}")
            backgroundImageView?.visibility = GONE
        }
    }

    fun stop() {
        removeCallbacks(finishedRunnable)
        setImageBitmap(null)
    }

    private fun setupFinishedRunnable() {
        removeCallbacks(finishedRunnable)
        listener?.onImagePrepared()

        val duration = GeneralPrefs.slideshowSpeed.toLong() * 1000
        val fadeDuration = GeneralPrefs.mediaFadeOutDuration.toLong()
        val durationMinusFade = duration - fadeDuration

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
