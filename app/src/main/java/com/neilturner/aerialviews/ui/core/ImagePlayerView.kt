package com.neilturner.aerialviews.ui.core

import android.content.Context
import android.os.Build.VERSION.SDK_INT
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import coil3.EventListener
import coil3.ImageLoader
import coil3.decode.Decoder
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.target.ImageViewTarget
import coil3.util.DebugLogger
import coil3.util.Logger
import com.neilturner.aerialviews.BuildConfig
import com.neilturner.aerialviews.models.enums.AerialMediaSource
import com.neilturner.aerialviews.models.enums.ImmichAuthType
import com.neilturner.aerialviews.models.enums.PhotoScale
import com.neilturner.aerialviews.models.enums.ProgressBarLocation
import com.neilturner.aerialviews.models.enums.ProgressBarType
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.models.prefs.ImmichMediaPrefs
import com.neilturner.aerialviews.models.videos.AerialMedia
import com.neilturner.aerialviews.services.InputStreamFetcher
import com.neilturner.aerialviews.ui.overlays.ProgressBarEvent
import com.neilturner.aerialviews.ui.overlays.ProgressState
import com.neilturner.aerialviews.utils.FirebaseHelper
import com.neilturner.aerialviews.utils.ServerConfig
import com.neilturner.aerialviews.utils.SslHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.kosert.flowbus.GlobalBus
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import timber.log.Timber
import kotlin.time.Duration.Companion.milliseconds

class ImagePlayerView : AppCompatImageView {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    private var listener: OnImagePlayerEventListener? = null
    private var finishedRunnable = Runnable { listener?.onImageFinished() }
    private var errorRunnable = Runnable { listener?.onImageError() }
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    private val progressBar =
        GeneralPrefs.progressBarLocation != ProgressBarLocation.DISABLED && GeneralPrefs.progressBarType != ProgressBarType.VIDEOS

    private var target = ImageViewTarget(this)

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
                onPlayerError()
            }
        }

    private val logger: Logger? = if (BuildConfig.DEBUG) DebugLogger() else null

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

    private fun buildGifDecoder(): Decoder.Factory =
        if (SDK_INT >= 28) {
            AnimatedImageDecoder.Factory()
        } else {
            GifDecoder.Factory()
        }

    private fun buildOkHttpClient(): OkHttpClient {
        val serverConfig = ServerConfig("", ImmichMediaPrefs.validateSsl)
        val okHttpClient = SslHelper().createOkHttpClient(serverConfig)
        return okHttpClient
            .newBuilder()
            .addInterceptor(ApiKeyInterceptor())
            .build()
    }

    private class ApiKeyInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
            val originalRequest = chain.request()
            val newRequest =
                when (ImmichMediaPrefs.authType) {
                    ImmichAuthType.API_KEY -> {
                        originalRequest
                            .newBuilder()
                            .addHeader("X-API-Key", ImmichMediaPrefs.apiKey)
                            .build()
                    }
                    else -> originalRequest
                }
            return chain.proceed(newRequest)
        }
    }

    fun setImage(media: AerialMedia) {
        try {
            coroutineScope.launch {
                when (media.source) {
                    AerialMediaSource.SAMBA -> {
                        val stream = ImagePlayerHelper.streamFromSambaFile(media.uri)
                        stream?.let {
                            loadImage(it)
                        }
                    }

                    AerialMediaSource.WEBDAV -> {
                        val stream = ImagePlayerHelper.streamFromWebDavFile(media.uri)
                        stream?.let {
                            loadImage(it)
                        }
                    }

                    else -> {
                        loadImage(media.uri)
                    }
                }
            }
        } catch (ex: Exception) {
            Timber.e(ex, "Exception while trying to load image: ${ex.message}")
            FirebaseHelper.logExceptionIfRecent(ex.cause)
            listener?.onImageError()
        }
    }

    private suspend fun loadImage(data: Any) {
        try {
            val request =
                ImageRequest
                    .Builder(context)
                    .data(data)
                    .size(this.width, this.height)
                    // .precision(Precision.INEXACT)
                    // .scale(Scale.FIT)
                    .target(target)
                    .build()
            imageLoader.execute(request)
        } catch (ex: Exception) {
            Timber.e(ex, "Exception while trying to load image: ${ex.message}")
            FirebaseHelper.logExceptionIfRecent(ex.cause)
            listener?.onImageError()
            return
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
