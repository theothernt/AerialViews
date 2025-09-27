package com.neilturner.aerialviews.ui.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.graphics.createBitmap
import coil3.EventListener
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.request.transformations
import coil3.size.Size
import coil3.target.ImageViewTarget
import coil3.transform.Transformation
import com.neilturner.aerialviews.R
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
import java.io.InputStream
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds

class BlurTransformation(private val radius: Float = 25f) : Transformation() {
    
    override val cacheKey: String = "${BlurTransformation::class.java.name}-$radius"
    
    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        return applyCanvasBlur(input, radius)
    }
    
    private fun applyCanvasBlur(bitmap: Bitmap, radius: Float): Bitmap {
        // Simple box blur implementation for older Android versions
        val width = bitmap.width
        val height = bitmap.height
        val blurredBitmap = createBitmap(width, height)
        val canvas = Canvas(blurredBitmap)
        
        // Apply a simple alpha-based blur effect
        val paint = Paint().apply {
            alpha = 180 // Make it slightly transparent for a blur-like effect
            isAntiAlias = true
        }
        
        // Draw multiple slightly offset copies to simulate blur
        val offset = (radius / 10f).coerceAtMost(5f)
        for (i in -2..2) {
            for (j in -2..2) {
                paint.alpha = (60 + (25 * (3 - abs(i) - abs(j)))).coerceIn(30, 180)
                canvas.drawBitmap(bitmap, i * offset, j * offset, paint)
            }
        }
        
        return blurredBitmap
    }
}

class ImagePlayerView : FrameLayout {
    constructor(context: Context) : super(context) {
        init()
    }
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init()
    }

    private lateinit var backgroundImageView: AppCompatImageView
    private lateinit var foregroundImageView: AppCompatImageView

    private var listener: OnImagePlayerEventListener? = null
    private var finishedRunnable = Runnable { listener?.onImageFinished() }
    private var errorRunnable = Runnable { listener?.onImageError() }
    private val ioScope = CoroutineScope(Dispatchers.IO)
    private val mainScope = CoroutineScope(Dispatchers.Main)
    private lateinit var target: ImageViewTarget
    private lateinit var backgroundTarget: ImageViewTarget
    private var pausedTimestamp: Long = 0
    private var totalDuration: Long = 0
    private var remainingDuration: Long = 0

    private val progressBar =
        GeneralPrefs.progressBarLocation != ProgressBarLocation.DISABLED && GeneralPrefs.progressBarType != ProgressBarType.VIDEOS

    private fun init() {
        LayoutInflater.from(context).inflate(R.layout.image_player_view, this, true)
        backgroundImageView = findViewById(R.id.background_image)
        foregroundImageView = findViewById(R.id.foreground_image)
        target = ImageViewTarget(foregroundImageView)
        backgroundTarget = ImageViewTarget(backgroundImageView)
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
                setScaleMode(result.image.width, result.image.height)
                setupFinishedRunnable()
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
            // .memoryCache(null)
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
                    // Create two separate streams for SAMBA
                    loadImageWithTwoStreams(media.uri, AerialMediaSource.SAMBA)
                }
                AerialMediaSource.WEBDAV -> {
                    // Create two separate streams for WebDAV
                    loadImageWithTwoStreams(media.uri, AerialMediaSource.WEBDAV)
                }
                else -> {
                    loadImageFromUri(media.uri)
                }
            }
        }
    }

    private suspend fun loadImageWithTwoStreams(uri: Uri, source: AerialMediaSource) {
        try {
            val stream1 = when (source) {
                AerialMediaSource.SAMBA -> ImagePlayerHelper.streamFromSambaFile(uri)
                AerialMediaSource.WEBDAV -> ImagePlayerHelper.streamFromWebDavFile(uri)
                else -> null
            }
            
            val stream2 = when (source) {
                AerialMediaSource.SAMBA -> ImagePlayerHelper.streamFromSambaFile(uri)
                AerialMediaSource.WEBDAV -> ImagePlayerHelper.streamFromWebDavFile(uri)
                else -> null
            }
            
            if (stream1 != null && stream2 != null) {
                // Load foreground image
                val foregroundRequest = ImageRequest.Builder(context)
                    .data(stream1)
                    .allowHardware(false)
                    .target(target)
                    .size(this.width, this.height)
                    .build()

                // Load background image with blur
                val backgroundRequest = ImageRequest.Builder(context)
                    .data(stream2)
                    .allowHardware(false)
                    .target(backgroundTarget)
                    .size(this.width, this.height)
                    .transformations(BlurTransformation(25f))
                    .build()
                
                // Execute both requests
                imageLoader.execute(backgroundRequest)
                imageLoader.execute(foregroundRequest)
            } else {
                // Fallback to single stream method if one fails
                val stream = stream1 ?: stream2
                if (stream != null) {
                    loadImageFromStream(stream)
                } else {
                    listener?.onImageError()
                }
            }
        } catch (ex: Exception) {
            Timber.e(ex, "Exception while loading image with two streams: ${ex.message}")
            if (GeneralPrefs.showMediaErrorToasts) {
                mainScope.launch {
                    val errorMessage = ex.localizedMessage ?: "Photo loading error occurred"
                    ToastHelper.show(context, errorMessage)
                }
            }
            listener?.onImageError()
        }
    }

    private suspend fun loadImageFromStream(stream: InputStream) {
        // For streams, we need to avoid the toBitmap conversion complexity
        // Just load into foreground and rely on the dual stream approach
        val request = ImageRequest.Builder(context)
            .data(stream)
            .allowHardware(false)
            .target(target)
            .size(this.width, this.height)
            .build()
        
        imageLoader.execute(request)
    }
    
    private suspend fun loadImageFromUri(data: Any?) {
        // For URIs, we can load directly twice as before
        val foregroundRequest =
            ImageRequest
                .Builder(context)
                .data(data)
                .allowHardware(false)
                .target(target)
                .size(this.width, this.height)
                .build()

        val backgroundRequest =
            ImageRequest
                .Builder(context)
                .data(data)
                .allowHardware(false)
                .target(backgroundTarget)
                .size(this.width, this.height)
                .transformations(BlurTransformation(25f))
                .build()
        
        // Execute both requests
        imageLoader.execute(backgroundRequest)
        imageLoader.execute(foregroundRequest)
    }

    fun stop() {
        removeCallbacks(finishedRunnable)
        foregroundImageView.setImageBitmap(null)
        backgroundImageView.setImageBitmap(null)
        pausedTimestamp = 0
        remainingDuration = 0
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
        val scaleType =
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

        // Apply scale type to both ImageViews
        foregroundImageView.scaleType = scaleType
        backgroundImageView.scaleType = ImageView.ScaleType.CENTER_CROP
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
