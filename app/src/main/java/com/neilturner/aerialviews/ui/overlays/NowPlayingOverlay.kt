package com.neilturner.aerialviews.ui.overlays

import android.content.Context
import android.transition.ChangeBounds
import android.transition.Fade
import android.transition.TransitionManager
import android.transition.TransitionSet
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isGone
import androidx.core.widget.TextViewCompat
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.NowPlayingFormat
import com.neilturner.aerialviews.models.enums.OverlayType
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.services.MusicEvent
import com.neilturner.aerialviews.utils.TrackNameShortener
import kotlinx.coroutines.delay
import me.kosert.flowbus.EventsReceiver
import me.kosert.flowbus.subscribe
import timber.log.Timber

class NowPlayingOverlay : AppCompatTextView {
    var type = OverlayType.MUSIC1
    var format = NowPlayingFormat.DISABLED

    private val receiver = EventsReceiver()
    private var trackInfo = MusicEvent()
    private var shouldUpdate = false
    private var isUpdating = false
    private val prefs = GeneralPrefs

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    init {
        TextViewCompat.setTextAppearance(this, R.style.OverlayText)
        visibility = GONE
    }

    fun updateFormat(format: NowPlayingFormat?) {
        this.format = format ?: NowPlayingFormat.DISABLED
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        receiver.subscribe(skipRetained = true) { newTrackInfo: MusicEvent ->
            Timber.i("$type: Subscribed for music updates...")
            if (trackInfo != newTrackInfo) {
                trackInfo = newTrackInfo
                if (!isUpdating) {
                    updateNowPlaying()
                } else {
                    shouldUpdate = true
                }
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        receiver.unsubscribe()
    }

    private suspend fun updateNowPlaying() {
        isUpdating = true

        if (alpha != 0f) {
            fadeOut()
        }

        shouldUpdate = false
        val shouldFadeIn = updateText()

        if (shouldFadeIn) {
            fadeIn()
        }

        animateOverlays()

        isUpdating = false

        if (shouldUpdate) {
            updateNowPlaying()
        }
    }

    private fun animateOverlays() {
        val layout: ConstraintLayout? = parent as ConstraintLayout

        TransitionManager.beginDelayedTransition(
            layout,
            TransitionSet().apply {
                ordering = TransitionSet.ORDERING_TOGETHER
                addTransition(Fade())
                addTransition(ChangeBounds())
                duration = 300
            },
        )

        if (!isGone && text.isNullOrBlank()) {
            Timber.i("$type: Transition... GONE")
            visibility = GONE
        } else if (isGone && text.isNotEmpty()) {
            Timber.i("$type: Transition... VISIBLE")
            visibility = VISIBLE
        }
    }

    private suspend fun fadeOut() {
        animate()
            .alpha(0f)
            .setDuration(300)
            .start()
        Timber.i("$type: Fading out...")
        delay(300)
    }

    private suspend fun fadeIn() {
        animate()
            .alpha(1f)
            .setDuration(300)
            .start()
        Timber.i("$type: Fading in...")
        delay(300)
    }

    private fun updateText(): Boolean {
        val updatedText = formatNowPlaying(trackInfo)
        return if (updatedText.isNotBlank()) {
            Timber.i("$type: Set new track info...")
            text = updatedText
            true
        } else {
            Timber.i("$type: Set text to NULL")
            text = null
            false
        }
    }

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
