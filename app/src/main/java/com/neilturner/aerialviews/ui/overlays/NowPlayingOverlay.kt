package com.neilturner.aerialviews.ui.overlays

import android.content.Context
import android.transition.ChangeBounds
import android.transition.Fade
import android.transition.TransitionManager
import android.transition.TransitionSet
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatTextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isGone
import androidx.core.widget.TextViewCompat
import coil3.ImageLoader
import coil3.asDrawable
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.ImageRequest
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.NowPlayingFormat
import com.neilturner.aerialviews.models.enums.OverlayType
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.services.MusicEvent
import com.neilturner.aerialviews.ui.helpers.FontHelper
import com.neilturner.aerialviews.ui.overlays.state.NowPlayingOverlayState
import com.neilturner.aerialviews.ui.overlays.utils.TrackNameShortener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import timber.log.Timber
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds

class NowPlayingOverlay
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : LinearLayout(context, attrs, defStyleAttr) {
    var type = OverlayType.MUSIC1
    var format = NowPlayingFormat.DISABLED
    var isHidden = false

    private var font = ""
    private var textSizeSp = 0f
    private var weight = ""

    private var trackInfo = MusicEvent()
    private var shouldUpdate = false
    private var isUpdating = false
    private val minVisibleAlphaForFade = 0.95f
    private val prefs = GeneralPrefs

    private val albumArtView: ImageView
    private val textView: AppCompatTextView

    private var scopeJob = SupervisorJob()
    private var mainScope = CoroutineScope(Dispatchers.Main + scopeJob)

    private val imageLoader by lazy {
        val client =
            OkHttpClient
                .Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build()
        ImageLoader
            .Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(client)) }
            .build()
    }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        visibility = GONE

        albumArtView =
            ImageView(context).apply {
                visibility = GONE
                scaleType = ImageView.ScaleType.CENTER_CROP
            }

        textView =
            AppCompatTextView(context).apply {
                TextViewCompat.setTextAppearance(this, R.style.OverlayText)
            }

        addView(albumArtView)
        addView(textView)
    }

    fun style(
        font: String,
        size: Float,
        weight: String,
    ) {
        this.font = font
        this.textSizeSp = size
        this.weight = weight
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        textView.typeface = FontHelper.getTypeface(context, font, weight)
        val marginPx = context.resources.getDimensionPixelSize(R.dimen.screen_border_padding)
        val screenWidth = context.resources.displayMetrics.widthPixels
        textView.maxWidth = screenWidth - marginPx * 2
    }

    fun updateFormat(format: NowPlayingFormat?) {
        this.format = format ?: NowPlayingFormat.DISABLED
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (scopeJob.isCancelled) {
            scopeJob = SupervisorJob()
            mainScope = CoroutineScope(Dispatchers.Main + scopeJob)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        scopeJob.cancel()
    }

    fun render(state: NowPlayingOverlayState) {
        val newTrackInfo = state.event
        if (trackInfo != newTrackInfo) {
            trackInfo = newTrackInfo
            if (!isUpdating) {
                mainScope.launch { updateNowPlaying() }
            } else {
                shouldUpdate = true
            }
        }
    }

    private suspend fun updateNowPlaying() {
        isUpdating = true

        if (alpha >= minVisibleAlphaForFade) {
            fadeOut()
        }

        shouldUpdate = false
        val shouldFadeIn = updateContent()

        if (shouldFadeIn && alpha < minVisibleAlphaForFade) {
            fadeIn()
        }

        animateOverlays()

        isUpdating = false

        if (shouldUpdate) {
            updateNowPlaying()
        }
    }

    private fun animateOverlays() {
        val layout: ConstraintLayout = parent as ConstraintLayout

        TransitionManager.beginDelayedTransition(
            layout,
            TransitionSet().apply {
                ordering = TransitionSet.ORDERING_TOGETHER
                addTransition(Fade())
                addTransition(ChangeBounds())
                duration = 300
            },
        )

        if (isHidden) {
            Timber.i("$type: Skipping visibility change, overlay is hidden")
            return
        }

        if (!isGone && textView.text.isNullOrBlank()) {
            Timber.i("$type: Transition... GONE")
            visibility = GONE
        } else if (isGone && !textView.text.isNullOrBlank()) {
            Timber.i("$type: Transition... VISIBLE")
            visibility = VISIBLE
        }
    }

    private suspend fun fadeOut() {
        animate().alpha(0f).setDuration(300).start()
        Timber.i("$type: Fading out...")
        delay(300.milliseconds)
    }

    private suspend fun fadeIn() {
        if (isHidden) {
            Timber.i("$type: Skipping fade-in, overlay is hidden")
            return
        }
        animate().alpha(1f).setDuration(300).start()
        Timber.i("$type: Fading in...")
        delay(300.milliseconds)
    }

    private fun updateContent(): Boolean {
        val updatedText = formatNowPlaying(trackInfo)
        val hasText = updatedText.isNotBlank()
        if (hasText) {
            Timber.i("$type: Set new track info...")
            textView.text = updatedText
        } else {
            Timber.i("$type: Set text to NULL")
            textView.text = null
        }
        updateAlbumArt(trackInfo.albumArtUri)
        return hasText
    }

    private fun updateAlbumArt(artUri: String) {
        if (artUri.isBlank()) {
            Timber.i("$type: No album art URI, hiding image view")
            albumArtView.visibility = GONE
            albumArtView.setImageDrawable(null)
            (textView.layoutParams as? LayoutParams)?.leftMargin = 0
            return
        }

        Timber.i("$type: Loading album art from $artUri")

        val artSize = calculateArtSize()
        albumArtView.layoutParams = LayoutParams(artSize, artSize)

        val textParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        textParams.leftMargin = 12.dpToPx()
        textView.layoutParams = textParams

        val request =
            ImageRequest
                .Builder(context)
                .data(artUri)
                .target(
                    onSuccess = { image ->
                        Timber.i("$type: Album art loaded successfully")
                        albumArtView.setImageDrawable(image.asDrawable(resources))
                        albumArtView.visibility = VISIBLE
                    },
                    onError = {
                        albumArtView.visibility = GONE
                    },
                ).listener(
                    onError = { _, result ->
                        Timber.w("$type: Album art load failed — ${result.throwable}")
                    },
                ).build()
        imageLoader.enqueue(request)
    }

    private fun calculateArtSize(): Int {
        if (textSizeSp <= 0f) return 48.dpToPx()
        val textPaint =
            TextView(context)
                .apply {
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
                    typeface = FontHelper.getTypeface(context, font, weight)
                }.paint
        val textHeight = textPaint.fontMetrics.let { it.descent - it.ascent }
        return (textHeight * 2.5f).toInt()
    }

    private fun Int.dpToPx(): Int =
        TypedValue
            .applyDimension(TypedValue.COMPLEX_UNIT_DIP, this.toFloat(), resources.displayMetrics)
            .toInt()

    private fun formatNowPlaying(trackInfo: MusicEvent): String {
        val (artist, song) = trackInfo
        val processedSong = if (prefs.nowPlayingShortenTrackName) TrackNameShortener.shortenTrackName(song) else song

        return when (format) {
            NowPlayingFormat.SONG_ARTIST -> {
                if (processedSong.isNotBlank() && artist.isNotBlank()) {
                    "$processedSong · $artist"
                } else {
                    processedSong.takeIf { it.isNotBlank() } ?: artist
                }
            }

            NowPlayingFormat.ARTIST_SONG -> {
                if (artist.isNotBlank() && processedSong.isNotBlank()) {
                    "$artist · $processedSong"
                } else {
                    artist.takeIf { it.isNotBlank() } ?: processedSong
                }
            }

            NowPlayingFormat.ARTIST -> artist

            NowPlayingFormat.SONG -> processedSong

            else -> ""
        }
    }
}
