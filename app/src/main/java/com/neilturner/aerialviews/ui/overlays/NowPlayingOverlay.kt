package com.neilturner.aerialviews.ui.overlays

import android.content.Context
import android.graphics.Bitmap
import android.transition.ChangeBounds
import android.transition.Fade
import android.transition.TransitionManager
import android.transition.TransitionSet
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatTextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isGone
import androidx.core.widget.TextViewCompat
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
import timber.log.Timber
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

        private var trackInfo = MusicEvent()
        private var shouldUpdate = false
        private var isUpdating = false
        private val minVisibleAlphaForFade = 0.95f
        private val prefs = GeneralPrefs

        private val albumArtView: ImageView
        private val textView: AppCompatTextView

        private var scopeJob = SupervisorJob()
        private var mainScope = CoroutineScope(Dispatchers.Main + scopeJob)

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
            textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
            textView.typeface = FontHelper.getTypeface(context, font, weight)
            textView.includeFontPadding = false
            val offset = FontHelper.getFontVerticalOffset(context, font, textView.textSize)
            textView.setPadding(0, offset, 0, -offset)
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
            updateAlbumArt(trackInfo.albumArt)
            return hasText
        }

        private fun updateAlbumArt(albumArt: Bitmap?) {
            if (albumArt == null) {
                albumArtView.visibility = GONE
                albumArtView.setImageDrawable(null)
                textView.layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
                return
            }

            // Album art is square, sized relative to the text so it scales with the font size
            val artSize = (textView.lineHeight * 2.5f).toInt()
            albumArtView.layoutParams = LayoutParams(artSize, artSize)
            albumArtView.setImageBitmap(albumArt)
            albumArtView.visibility = VISIBLE

            textView.layoutParams =
                LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                    leftMargin = 12.dpToPx()
                }
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

                NowPlayingFormat.ARTIST -> {
                    artist
                }

                NowPlayingFormat.SONG -> {
                    processedSong
                }

                else -> {
                    ""
                }
            }
        }
    }
