package com.neilturner.aerialviews.ui.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
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
import coil3.size.Size
import coil3.target.ImageViewTarget
import coil3.toBitmap
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
import com.neilturner.aerialviews.utils.ColourHelper
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
    
    // Background preference
    private var backgroundPreference: String = "BLACK" // Default to black
    
    // Simple blur preprocessing cache
    private var preprocessedBlur: Bitmap? = null
    private var preprocessedUri: String? = null
    
    

    private val progressBar =
        GeneralPrefs.progressBarLocation != ProgressBarLocation.DISABLED && GeneralPrefs.progressBarType != ProgressBarType.VIDEOS

    private fun init() {
        LayoutInflater.from(context).inflate(R.layout.image_player_view, this, true)
        backgroundImageView = findViewById(R.id.background_image)
        foregroundImageView = findViewById(R.id.foreground_image)
        target = ImageViewTarget(foregroundImageView)
        backgroundTarget = ImageViewTarget(backgroundImageView)
        
        // Initially hide foreground image until background blur is ready
        foregroundImageView.visibility = android.view.View.INVISIBLE
    }

    fun release() {
        removeCallbacks(finishedRunnable)
        removeCallbacks(errorRunnable)
        listener = null
    }
    
    fun setBackgroundPreference(preference: String) {
        backgroundPreference = preference
        Timber.d("Background preference set to: $preference")
    }

    private val eventLister =
        object : EventListener() {
            override fun onSuccess(
                request: ImageRequest,
                result: SuccessResult,
            ) {
                super.onSuccess(request, result)
                
                // Handle different background options
                if (ColourHelper.isBlurredBackground(backgroundPreference)) {
                    // Create blurred background
                    ioScope.launch {
                        try {
                            val bitmap = result.image.toBitmap()
                            val blurredBitmap = GPUBlurTransformation(25f, context).transform(bitmap, Size(bitmap.width, bitmap.height))
                            
                            // Set both images simultaneously on main thread
                            withContext(Dispatchers.Main) {
                                setScaleMode(result.image.width, result.image.height)
                                backgroundImageView.setImageBitmap(blurredBitmap)
                                foregroundImageView.visibility = android.view.View.VISIBLE
                                setupFinishedRunnable()
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Error creating blurred background")
                            // Fallback: just use the original image for background
                            withContext(Dispatchers.Main) {
                                setScaleMode(result.image.width, result.image.height)
                                backgroundImageView.setImageBitmap(result.image.toBitmap())
                                foregroundImageView.visibility = android.view.View.VISIBLE
                                setupFinishedRunnable()
                            }
                        }
                    }
                } else {
                    // Use solid color background (BLACK or WHITE)
                    val backgroundColor = ColourHelper.colourFromString(backgroundPreference)
                    ioScope.launch {
                        withContext(Dispatchers.Main) {
                            setScaleMode(result.image.width, result.image.height)
                            backgroundImageView.setImageBitmap(null) // Clear any existing background
                            backgroundImageView.setBackgroundColor(backgroundColor)
                            foregroundImageView.visibility = android.view.View.VISIBLE
                            setupFinishedRunnable()
                        }
                    }
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
            // .memoryCache(null)
            .logger(logger)
            .eventListener(eventLister)
            .components {
                add(OkHttpNetworkFetcherFactory(buildOkHttpClient()))
                add(InputStreamFetcher.Factory())
                add(buildGifDecoder())
            }.build()

    fun setImage(media: AerialMedia) {
        Timber.d("setImage called for: ${media.uri}")
        
        // Always clear existing images first to prevent recycled bitmap issues
        backgroundImageView.setImageBitmap(null)
        foregroundImageView.setImageBitmap(null)
        foregroundImageView.visibility = android.view.View.INVISIBLE
        
        // Disable preprocessing to avoid background mismatch issues
        // Check if we have preprocessed blur for this exact image
        // if (preprocessedBlur != null && preprocessedUri == media.uri.toString()) {
        //     Timber.d("Using preprocessed blur for instant display")
        //     // Use preprocessed blur - just load the foreground image
        //     loadImageWithPreprocessedBlur(media)
        //     return
        // }
        Timber.d("Loading image normally (preprocessing disabled)")
        
        // Load image normally
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
    
    private fun loadImageWithPreprocessedBlur(media: AerialMedia) {
        val blur = preprocessedBlur ?: return
        
        // Load the foreground image normally, but use preprocessed blur for background
        ioScope.launch {
            when (media.source) {
                AerialMediaSource.SAMBA -> {
                    loadImageWithTwoStreamsAndPreprocessedBlur(media.uri, AerialMediaSource.SAMBA, blur)
                }
                AerialMediaSource.WEBDAV -> {
                    loadImageWithTwoStreamsAndPreprocessedBlur(media.uri, AerialMediaSource.WEBDAV, blur)
                }
                else -> {
                    loadImageFromUriWithPreprocessedBlur(media.uri, blur)
                }
            }
        }
        
        // Clear preprocessed data
        preprocessedBlur = null
        preprocessedUri = null
    }
    
    fun preprocessNextImage(media: AerialMedia) {
        Timber.d("preprocessNextImage called for: ${media.uri}")
        
        // Only preprocess if using blurred background
        if (!ColourHelper.isBlurredBackground(backgroundPreference)) {
            Timber.d("Skipping blur preprocessing - not using blurred background")
            return
        }
        
        // Start preprocessing the blur for the next image in background
        ioScope.launch {
            try {
                val request = ImageRequest.Builder(context)
                    .data(media.uri)
                    .allowHardware(false)
                    .size(this@ImagePlayerView.width, this@ImagePlayerView.height)
                    .build()
                
                val result = imageLoader.execute(request)
                if (result is SuccessResult) {
                    val bitmap = result.image.toBitmap()
                    val blurredBitmap = GPUBlurTransformation(25f, context).transform(bitmap, Size(bitmap.width, bitmap.height))
                    
                    // Cache the preprocessed blur (but don't apply it to background ImageView yet)
                    preprocessedBlur = blurredBitmap
                    preprocessedUri = media.uri.toString()
                    Timber.d("Blur preprocessing completed for: ${media.uri} (cached, not applied)")
                } else {
                    Timber.d("Blur preprocessing failed for: ${media.uri}")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error preprocessing blur for: ${media.uri}")
            }
        }
    }

    private suspend fun loadImageWithTwoStreams(uri: Uri, source: AerialMediaSource) {
        try {
            val stream = when (source) {
                AerialMediaSource.SAMBA -> ImagePlayerHelper.streamFromSambaFile(uri)
                AerialMediaSource.WEBDAV -> ImagePlayerHelper.streamFromWebDavFile(uri)
                else -> null
            }
            
            if (stream != null) {
                loadImageFromStream(stream)
            } else {
                listener?.onImageError()
            }
        } catch (ex: Exception) {
            Timber.e(ex, "Exception while loading image with stream: ${ex.message}")
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
        val request = ImageRequest.Builder(context)
            .data(stream)
            .allowHardware(false)
            .target(target)
            .size(this.width, this.height)
            .build()
        
        imageLoader.execute(request)
    }
    
    private suspend fun loadImageFromUri(data: Any?) {
        val request =
            ImageRequest
                .Builder(context)
                .data(data)
                .allowHardware(false)
                .target(target)
                .size(this.width, this.height)
                .build()

        imageLoader.execute(request)
    }
    
    private suspend fun loadImageWithTwoStreamsAndPreprocessedBlur(uri: Uri, source: AerialMediaSource, preprocessedBlur: Bitmap) {
        try {
            val stream = when (source) {
                AerialMediaSource.SAMBA -> ImagePlayerHelper.streamFromSambaFile(uri)
                AerialMediaSource.WEBDAV -> ImagePlayerHelper.streamFromWebDavFile(uri)
                else -> null
            }
            
            if (stream != null) {
                loadImageFromStreamWithPreprocessedBlur(stream, preprocessedBlur)
            } else {
                Timber.e("Failed to get stream for $source")
            }
        } catch (ex: Exception) {
            Timber.e(ex, "Exception while loading image with stream: ${ex.message}")
        }
    }
    
    private suspend fun loadImageFromStreamWithPreprocessedBlur(stream: InputStream, preprocessedBlur: Bitmap) {
        val request = ImageRequest.Builder(context)
            .data(stream)
            .allowHardware(false)
            .target(object : ImageViewTarget(foregroundImageView) {
                override fun onSuccess(image: coil3.Image) {
                    // Set both foreground and preprocessed background simultaneously
                    setScaleMode(image.width, image.height)
                    backgroundImageView.setImageBitmap(preprocessedBlur)
                    foregroundImageView.setImageDrawable(image as Drawable)
                    foregroundImageView.visibility = android.view.View.VISIBLE
                    setupFinishedRunnable()
                }
                
                override fun onError(error: coil3.Image?) {
                    listener?.onImageError()
                }
            })
            .size(this.width, this.height)
            .build()
        
        imageLoader.execute(request)
    }
    
    private suspend fun loadImageFromUriWithPreprocessedBlur(data: Any?, preprocessedBlur: Bitmap) {
        val request = ImageRequest.Builder(context)
            .data(data)
            .allowHardware(false)
            .target(object : ImageViewTarget(foregroundImageView) {
                override fun onSuccess(image: coil3.Image) {
                    // Set both foreground and preprocessed background simultaneously
                    setScaleMode(image.width, image.height)
                    backgroundImageView.setImageBitmap(preprocessedBlur)
                    foregroundImageView.setImageDrawable(image as Drawable)
                    foregroundImageView.visibility = android.view.View.VISIBLE
                    setupFinishedRunnable()
                }
                
                override fun onError(error: coil3.Image?) {
                    listener?.onImageError()
                }
            })
            .size(this.width, this.height)
            .build()

        imageLoader.execute(request)
    }
    

    fun stop() {
        removeCallbacks(finishedRunnable)
        
        // Clear both images completely
        foregroundImageView.setImageBitmap(null)
        backgroundImageView.setImageBitmap(null)
        backgroundImageView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        foregroundImageView.visibility = android.view.View.INVISIBLE
        
        // Clear preprocessed data
        preprocessedBlur = null
        preprocessedUri = null
        
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
