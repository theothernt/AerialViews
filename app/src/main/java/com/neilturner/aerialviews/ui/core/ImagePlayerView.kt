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
