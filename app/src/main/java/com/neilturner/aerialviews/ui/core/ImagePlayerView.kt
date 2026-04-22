package com.neilturner.aerialviews.ui.core

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Matrix
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.animation.LinearInterpolator
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
import me.kosert.flowbus.GlobalBus
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.PushbackInputStream
import kotlin.time.Duration.Companion.milliseconds

class ImagePlayerView : FrameLayout {
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

    private var kenBurnsAnimator: ValueAnimator? = null
    private var pendingKenBurns: KenBurnsSetup? = null

    /**
     * Precomputed state for one vertical pan. Start/end translate-Y are the matrix
     * translation values at the beginning and end of the animation respectively
     * (0 = image top at container top, negative = image scrolled up / pan shows lower part).
     */
    private data class KenBurnsSetup(
        val scale: Float,
        val overflow: Float,
        val startTranslateY: Float,
        val endTranslateY: Float,
    )

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

    fun setImage(mediaParam: AerialMedia) {
        // If this logical slot carries alternates (e.g. other members of a temporal
        // cluster from the Immich smart slideshow), pick one at random every time
        // the slot is rendered so the user sees variety across playlist loops.
        // Each alternate carries its own per-photo subjectRect — cluster gap can span
        // a full day, so inheriting the primary's face bbox would be geometrically wrong.
        val media =
            if (mediaParam.clusterAlternates.isNotEmpty()) {
                val pick = (0..mediaParam.clusterAlternates.size).random()
                if (pick == 0) {
                    mediaParam
                } else {
                    val alt = mediaParam.clusterAlternates[pick - 1]
                    mediaParam.copy(
                        uri = alt.uri,
                        metadata = mediaParam.metadata.copy(subjectRect = alt.subjectRect),
                    )
                }
            } else {
                mediaParam
            }
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
                            setScaleMode(result.image.width, result.image.height, media.metadata.subjectRect)
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
        kenBurnsAnimator?.cancel()
        kenBurnsAnimator = null
        pendingKenBurns = null
        setForegroundDrawable(null)
        pausedTimestamp = 0
        remainingDuration = 0
        blurHelper.cancel()
    }

    fun pauseTimer() {
        pausedTimestamp = System.currentTimeMillis()
        removeCallbacks(finishedRunnable)
        kenBurnsAnimator?.takeIf { it.isRunning }?.pause()
    }

    fun resumeTimer(pauseDuration: Long) {
        if (pausedTimestamp > 0) {
            remainingDuration = maxOf(0, remainingDuration - pauseDuration)
            if (remainingDuration > 0) {
                postDelayed(finishedRunnable, remainingDuration)
                kenBurnsAnimator?.takeIf { it.isPaused }?.resume()
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
        subjectRect: com.neilturner.aerialviews.models.videos.NormalizedRect? = null,
    ) {
        kenBurnsAnimator?.cancel()
        pendingKenBurns = null

        val aspect = AspectRatio.fromDimensions(width, height)
        val pref =
            when (aspect) {
                AspectRatio.SQUARE, AspectRatio.PORTRAIT -> GeneralPrefs.photoScalePortrait
                AspectRatio.LANDSCAPE -> GeneralPrefs.photoScaleLandscape
            }

        // Face-aware logic only applies to true portraits (h > w) so landscape/square
        // behavior stays exactly as it was.
        val faceRect =
            if (aspect == AspectRatio.PORTRAIT && GeneralPrefs.photoScaleFaceAware) subjectRect else null
        Timber.i("Aspect ratio: $aspect")

        if (pref == PhotoScale.KEN_BURNS_VERTICAL) {
            val setup = prepareVerticalPan(width, height, faceRect)
            if (setup != null) {
                foregroundImageView.scaleType = ImageView.ScaleType.MATRIX
                applyVerticalPanMatrix(setup, setup.startTranslateY)
                pendingKenBurns = setup
                return
            }
        }

        // Static face-biased center-crop (rule-of-thirds) for portraits when face-aware is on.
        if (pref == PhotoScale.CENTER_CROP && faceRect != null) {
            if (applyFaceBiasedCenterCropMatrix(width, height, faceRect)) return
        }

        foregroundImageView.scaleType = resolveForegroundScaleType(width, height)
    }

    private fun resolveForegroundScaleType(
        width: Int,
        height: Int,
    ): ImageView.ScaleType {
        val aspect = AspectRatio.fromDimensions(width, height)
        return when (aspect) {
            AspectRatio.SQUARE, AspectRatio.PORTRAIT -> getScaleType(GeneralPrefs.photoScalePortrait)
            AspectRatio.LANDSCAPE -> getScaleType(GeneralPrefs.photoScaleLandscape)
        }
    }

    private fun getScaleType(scale: PhotoScale?): ImageView.ScaleType =
        when (scale) {
            PhotoScale.CENTER_CROP -> ImageView.ScaleType.CENTER_CROP
            PhotoScale.FIT_CENTER -> ImageView.ScaleType.FIT_CENTER
            // KEN_BURNS_VERTICAL on images that don't qualify (landscape, square, or container
            // not yet measured) falls back to center-crop so playback never breaks.
            PhotoScale.KEN_BURNS_VERTICAL, null -> ImageView.ScaleType.CENTER_CROP
        }

    /**
     * Compute matrix scale + start/end translate-Y for a vertical pan on a taller-than-container
     * image. When [subjectRect] is provided, the pan range is narrowed so subject + margin stays
     * in view throughout, and direction is chosen so the pan starts from the side where the
     * subject lives. Otherwise the full overflow is traversed with direction alternating per slot.
     */
    private fun prepareVerticalPan(
        imageWidth: Int,
        imageHeight: Int,
        subjectRect: com.neilturner.aerialviews.models.videos.NormalizedRect? = null,
    ): KenBurnsSetup? {
        val view = foregroundImageView
        val containerW = view.width
        val containerH = view.height
        if (containerW <= 0 || containerH <= 0 || imageWidth <= 0 || imageHeight <= 0) return null
        val scale = containerW.toFloat() / imageWidth.toFloat()
        val overflow = imageHeight.toFloat() * scale - containerH.toFloat()
        if (overflow <= 0f) return null

        val (startY, endY) =
            if (subjectRect != null) {
                computeFaceBiasedPanRange(overflow, imageWidth.toFloat(), imageHeight.toFloat(), subjectRect)
            } else {
                val downward = (System.currentTimeMillis() / 1000L) % 2L == 0L
                if (downward) Pair(0f, -overflow) else Pair(-overflow, 0f)
            }
        return KenBurnsSetup(scale = scale, overflow = overflow, startTranslateY = startY, endTranslateY = endY)
    }

    /**
     * Work out pan start/end translate-Y around the face subject, starting from whichever
     * end the face sits closer to. The allowed face-off-screen fraction (0..1 of face
     * height) is read from [GeneralPrefs.photoScaleFaceOffScreenPercent]: 0 keeps face
     * fully in view at pan extremes, larger values relax the constraint and widen the
     * pan for a more cinematic "face enters / face leaves" feel.
     */
    private fun computeFaceBiasedPanRange(
        overflowPx: Float,
        imageWidthF: Float,
        imageHeightF: Float,
        face: com.neilturner.aerialviews.models.videos.NormalizedRect,
    ): Pair<Float, Float> {
        val offScreenFrac = (GeneralPrefs.photoScaleFaceOffScreenPercent.toIntOrNull() ?: 0)
            .coerceIn(0, 100) / 100f
        val marginFrac = -offScreenFrac * face.height
        val view = foregroundImageView
        val containerW = view.width.toFloat()
        val containerH = view.height.toFloat()
        // Band of the image (in image-y pixels) that fits in the container at any instant.
        val bandImgY = containerH * imageWidthF / containerW
        val total = imageHeightF // image-y total range (pixels)
        val slide = total - bandImgY // how many image-y pixels the band can slide
        if (slide <= 0f) return Pair(0f, 0f)

        val faceTopPx = (face.top - marginFrac).coerceIn(0f, 1f) * total
        val faceBotPx = (face.bottom + marginFrac).coerceIn(0f, 1f) * total
        val faceCenterFrac = (face.top + face.bottom) / 2f

        // Solve constraint: visible band [P*slide, P*slide+bandImgY] contains [faceTop, faceBot].
        val pMinRaw = (faceBotPx - bandImgY) / slide
        val pMaxRaw = faceTopPx / slide
        val pMin = pMinRaw.coerceIn(0f, 1f)
        val pMax = pMaxRaw.coerceIn(0f, 1f)

        // If face + margin is bigger than the band, center the band on the face center.
        val (pStart, pEnd) =
            if (pMin > pMax) {
                val pCenter = ((faceCenterFrac * total - bandImgY / 2f) / slide).coerceIn(0f, 1f)
                Pair(pCenter, pCenter)
            } else if (faceCenterFrac < 0.5f) {
                Pair(pMin, pMax)
            } else {
                Pair(pMax, pMin)
            }
        // translateY = -P * overflow (because larger P means image scrolled up more)
        return Pair(-pStart * overflowPx, -pEnd * overflowPx)
    }

    /**
     * Place the portrait image with MATRIX scale type biased toward the face — subject's
     * eye-line near the rule-of-thirds mark (1/3 from top of visible band). Returns false
     * if the container isn't measured yet, so the caller can fall back to plain CENTER_CROP.
     */
    private fun applyFaceBiasedCenterCropMatrix(
        imageWidth: Int,
        imageHeight: Int,
        face: com.neilturner.aerialviews.models.videos.NormalizedRect,
    ): Boolean {
        val view = foregroundImageView
        val containerW = view.width
        val containerH = view.height
        if (containerW <= 0 || containerH <= 0 || imageWidth <= 0 || imageHeight <= 0) return false
        val scale = containerW.toFloat() / imageWidth.toFloat()
        val overflow = imageHeight.toFloat() * scale - containerH.toFloat()
        if (overflow <= 0f) return false

        val bandImgY = containerH.toFloat() * imageWidth.toFloat() / containerW.toFloat()
        val slide = imageHeight.toFloat() - bandImgY
        // Target: face center at 1/3 from top of visible band.
        val faceCy = (face.top + face.bottom) / 2f
        val p = ((faceCy * imageHeight.toFloat() - bandImgY / 3f) / slide).coerceIn(0f, 1f)

        val setup = KenBurnsSetup(scale = scale, overflow = overflow, startTranslateY = 0f, endTranslateY = 0f)
        foregroundImageView.scaleType = ImageView.ScaleType.MATRIX
        applyVerticalPanMatrix(setup, -p * overflow)
        return true
    }

    private fun applyVerticalPanMatrix(
        setup: KenBurnsSetup,
        translateY: Float,
    ) {
        val m = Matrix()
        m.setScale(setup.scale, setup.scale)
        m.postTranslate(0f, translateY)
        foregroundImageView.imageMatrix = m
    }

    private fun startKenBurnsIfPending(durationMs: Long) {
        val setup = pendingKenBurns ?: return
        if (durationMs <= 0) return
        if (setup.startTranslateY == setup.endTranslateY) return // face too big to pan — stay static
        kenBurnsAnimator =
            ValueAnimator.ofFloat(setup.startTranslateY, setup.endTranslateY).apply {
                duration = durationMs
                interpolator = LinearInterpolator()
                addUpdateListener { applyVerticalPanMatrix(setup, it.animatedValue as Float) }
                start()
            }
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
        if (progressBar) GlobalBus.post(ProgressBarEvent(ProgressState.START, 0, duration))
        startKenBurnsIfPending(duration)
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
