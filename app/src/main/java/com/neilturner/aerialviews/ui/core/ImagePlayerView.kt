package com.neilturner.aerialviews.ui.core

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import coil3.EventListener
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
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
    private var pausedTimestamp: Long = 0
    private var totalDuration: Long = 0
    private var remainingDuration: Long = 0

    private val progressBar =
        GeneralPrefs.progressBarLocation != ProgressBarLocation.DISABLED && GeneralPrefs.progressBarType != ProgressBarType.VIDEOS

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
                    val errorMessage = ex.localizedMessage ?: "Photo loading error occurred"
                    ToastHelper.show(context, errorMessage)
                }
            }

            listener?.onImageError()
        }
    }

    fun stop() {
        removeCallbacks(finishedRunnable)
        setImageBitmap(null)
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
        scaleType =
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

    private fun getScaleType(scale: PhotoScale?): ScaleType =
        try {
            ScaleType.valueOf(scale.toString())
        } catch (e: Exception) {
            Timber.e(e)
            ScaleType.valueOf(PhotoScale.CENTER_CROP.toString())
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
