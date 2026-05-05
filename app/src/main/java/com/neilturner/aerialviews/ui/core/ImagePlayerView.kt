package com.neilturner.aerialviews.ui.core

import android.content.Context
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatImageView
import coil3.ImageLoader
import coil3.asDrawable
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.ImageRequest
import com.hierynomus.protocol.transport.TransportException
import com.hierynomus.smbj.common.SMBRuntimeException
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
import com.neilturner.aerialviews.utils.FirebaseHelper
import com.neilturner.aerialviews.utils.ToastHelper
import com.neilturner.aerialviews.utils.filename
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import com.neilturner.aerialviews.services.PlaybackProgressRepository
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.PushbackInputStream
import kotlin.time.Duration.Companion.milliseconds

class ImagePlayerView : FrameLayout, KoinComponent {
    private val progressRepository: PlaybackProgressRepository by inject()
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    private var listener: OnImagePlayerEventListener? = null
    private var finishedRunnable = Runnable { listener?.onImageFinished() }
    private var errorRunnable = Runnable { listener?.onImageError() }

    private val ioJob = SupervisorJob()
    private val mainJob = SupervisorJob()
    private val ioScope = CoroutineScope(Dispatchers.IO + ioJob)
    private val mainScope = CoroutineScope(Dispatchers.Main + mainJob)

    private var pausedTimestamp: Long = 0
    private var totalDuration: Long = 0
    private var remainingDuration: Long = 0

    /**
     * Two-token sync gate: ensures [runSetupFinishedRunnable] fires only after *both*
     * the foreground image load (Coil onSuccess) and the background blur (potentially
     * async on pre-S devices) have completed for the same image request.
     *
     * - [backgroundReadyToken] is set by [markBackgroundReady] when [BackgroundBlurHelper]
     *   reports its background job is done.
     * - [pendingSetupToken] is set by [setupFinishedRunnable] when the foreground load
     *   finishes but the background isn't ready yet.
     * - [runSetupFinishedRunnable] fires when both tokens agree on the same value.
     */
    private var backgroundReadyToken: Long = -1
    private var pendingSetupToken: Long = -1

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
        GeneralPrefs.progressBarLocation != ProgressBarLocation.DISABLED &&
            GeneralPrefs.progressBarType != ProgressBarType.VIDEOS

    private val blurHelper =
        BackgroundBlurHelper(
            backgroundImageView = backgroundImageView,
            ioScope = ioScope,
            mainScope = mainScope,
            resolveTargetSize = ::resolveTargetSize,
            onReady = ::markBackgroundReady,
        )

    init {
        addView(backgroundImageView)
        addView(foregroundImageView)
    }

    fun release() {
        ioJob.cancel()
        mainJob.cancel()
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

    fun load(media: AerialMedia) {
        ioScope.launch {
            val baseStream = ImagePlayerHelper.streamFromMedia(context, media)
            if (baseStream == null) {
                loadImage(media, media.uri)
                return@launch
            }

            if (media.source == AerialMediaSource.IMMICH) {
                loadImage(media, baseStream)
                return@launch
            }

            val totalStartTime = System.currentTimeMillis()
            val stream =
                PushbackInputStream(
                    BufferedInputStream(baseStream, STREAM_BUFFER_SIZE),
                    BitmapHelper.HEADER_BUFFER_SIZE,
                )

            try {
                val headerBytes = ByteArray(BitmapHelper.HEADER_BUFFER_SIZE)
                val headerLength = readUpTo(stream, headerBytes, headerBytes.size)
                if (headerLength <= 0) {
                    stream.close()
                    loadImage(media, media.uri)
                    return@launch
                }
                stream.unread(headerBytes, 0, headerLength)

                val exifMetadata = BitmapHelper.extractExifMetadataFromHeader(headerBytes, headerLength)

                if (media.source != AerialMediaSource.IMMICH) {
                    media.metadata.exif.date = exifMetadata.date
                    media.metadata.exif.offset = exifMetadata.offset
                    media.metadata.exif.latitude = exifMetadata.latitude
                    media.metadata.exif.longitude = exifMetadata.longitude
                    media.metadata.exif.description = exifMetadata.description
                }

                Timber.d(
                    "ImagePlayerView: Extracted EXIF in ${System.currentTimeMillis() - totalStartTime}ms...",
                )

                loadImage(media, stream)
            } catch (e: TransportException) {
                Timber.e(e, "SMB transport dropped while reading image header")
                stream.close()
                onPlayerError()
            } catch (e: SMBRuntimeException) {
                Timber.e(e, "SMB runtime error while reading image header")
                stream.close()
                onPlayerError()
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error reading image stream")
                stream.close()
                onPlayerError()
            }
        }
    }

    private fun loadImage(
        media: AerialMedia,
        data: Any?,
    ) {
        // Errors from the actual async network/disk load are delivered via onError, not here.
        try {
            val (targetWidth, targetHeight) = resolveTargetSize()
            val request =
                ImageRequest
                    .Builder(context)
                    .data(data)
                    .size(targetWidth, targetHeight)
                    .target(
                        onStart = {
                            // resetImageTransforms()
                        },
                        onSuccess = { image ->
                            val drawable = image.asDrawable(resources)
                            blurHelper.update(drawable.takeIf { shouldShowBlurBackground(media, it) })
                            setForegroundDrawable(drawable)
                        },
                    ).listener(
                        onSuccess = { _, result ->
                            setScaleMode(result.image.width, result.image.height)
                            setupFinishedRunnable()
                        },
                        onError = { _, result ->
                            handleImageError(result.throwable)
                        },
                    ).build()
            imageLoader.enqueue(request)
        } catch (ex: Exception) {
            Timber.e(ex, "Exception while trying to load image: ${ex.message}")

            if (data is InputStream) {
                try {
                    data.close()
                } catch (_: Exception) {
                    // ignore
                }
            }

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

    private fun readUpTo(
        stream: InputStream,
        buffer: ByteArray,
        maxBytes: Int,
    ): Int {
        var total = 0
        val limit = minOf(maxBytes, buffer.size)
        while (total < limit) {
            val read = stream.read(buffer, total, limit - total)
            if (read <= 0) break
            total += read
        }
        return total
    }

    private fun setForegroundDrawable(drawable: Drawable?) {
        (foregroundImageView.drawable as? Animatable)?.stop()
        foregroundImageView.setImageDrawable(drawable)
        (drawable as? Animatable)?.start()
    }

    private fun shouldSkipBlurBackground(
        media: AerialMedia,
        drawable: Drawable,
    ): Boolean = media.uri.filename.endsWith(".gif", ignoreCase = true) || drawable is Animatable

    private fun shouldShowBlurBackground(
        media: AerialMedia,
        drawable: Drawable,
    ): Boolean {
        if (shouldSkipBlurBackground(media, drawable)) {
            return false
        }

        val imageWidth = drawable.intrinsicWidth
        val imageHeight = drawable.intrinsicHeight
        if (imageWidth <= 0 || imageHeight <= 0) {
            return false
        }

        if (resolveForegroundScaleType(imageWidth, imageHeight) != ImageView.ScaleType.FIT_CENTER) {
            return false
        }

        val (containerWidth, containerHeight) = resolveTargetSize()
        if (containerWidth <= 0 || containerHeight <= 0) {
            return false
        }

        val scale =
            minOf(
                containerWidth.toFloat() / imageWidth.toFloat(),
                containerHeight.toFloat() / imageHeight.toFloat(),
            )
        val displayedWidth = imageWidth * scale
        val displayedHeight = imageHeight * scale
        val epsilon = 0.5f

        return displayedWidth < containerWidth - epsilon || displayedHeight < containerHeight - epsilon
    }

    companion object {
        private const val STREAM_BUFFER_SIZE = 64 * 1024 // 64KB - helps reduce network round-trips
        private const val ERROR_DELAY = 5000L
    }

    private fun resolveTargetSize(): Pair<Int, Int> {
        val width = if (this.width > 0) this.width else resources.displayMetrics.widthPixels
        val height = if (this.height > 0) this.height else resources.displayMetrics.heightPixels
        return Pair(width, height)
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
        setForegroundDrawable(null)
        pausedTimestamp = 0
        remainingDuration = 0
        blurHelper.cancel()
    }

    fun pause() {
        pausedTimestamp = System.currentTimeMillis()
        removeCallbacks(finishedRunnable)
    }

    fun play() {
        if (pausedTimestamp > 0) {
            val pauseDuration = System.currentTimeMillis() - pausedTimestamp
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
        foregroundImageView.scaleType = resolveForegroundScaleType(width, height)
    }

    private fun resolveForegroundScaleType(
        width: Int,
        height: Int,
    ): ImageView.ScaleType {
        val aspect = AspectRatio.fromDimensions(width, height)
        Timber.i("Aspect ratio: $aspect")
        return when (aspect) {
            AspectRatio.SQUARE, AspectRatio.PORTRAIT -> getScaleType(GeneralPrefs.photoScalePortrait)
            AspectRatio.LANDSCAPE -> getScaleType(GeneralPrefs.photoScaleLandscape)
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
        val token = blurHelper.currentToken
        if (backgroundReadyToken == token) {
            runSetupFinishedRunnable()
        } else {
            pendingSetupToken = token
        }
    }

    private fun runSetupFinishedRunnable() {
        listener?.onImagePrepared()

        val duration = GeneralPrefs.slideshowSpeed.toLong() * 1000
        val fadeDuration = GeneralPrefs.mediaFadeOutDuration.toLong()
        val durationMinusFade = duration - fadeDuration

        totalDuration = duration
        remainingDuration = durationMinusFade

        Timber.i("Delay: ${durationMinusFade.milliseconds}")
        if (progressBar) progressRepository.post(ProgressBarEvent(ProgressState.START, 0, duration))
        postDelayed(finishedRunnable, durationMinusFade)
    }

    private fun markBackgroundReady(token: Long) {
        backgroundReadyToken = token
        if (pendingSetupToken == token) {
            pendingSetupToken = -1
            runSetupFinishedRunnable()
        }
    }

    private fun onPlayerError() {
        removeCallbacks(finishedRunnable)
        postDelayed(errorRunnable, ERROR_DELAY)
    }

    fun setListener(listener: OnImagePlayerEventListener?) {
        this.listener = listener
    }

    interface OnImagePlayerEventListener {
        fun onImageFinished()

        fun onImageError()

        fun onImagePrepared()
    }
}
