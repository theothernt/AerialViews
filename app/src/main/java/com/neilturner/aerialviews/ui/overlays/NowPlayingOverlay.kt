package com.neilturner.aerialviews.ui.overlays

import android.content.Context
import android.transition.ChangeBounds
import android.transition.Fade
import android.transition.TransitionManager
import android.transition.TransitionSet
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import androidx.core.widget.TextViewCompat
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.NowPlayingFormat
import com.neilturner.aerialviews.models.enums.OverlayType
import com.neilturner.aerialviews.services.MusicEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    private val animationDuration = 400L

    private val mainScope = CoroutineScope(Dispatchers.Main)

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    init {
        TextViewCompat.setTextAppearance(this, R.style.OverlayText)
        visibility = GONE
        alpha = 0f
    }

    fun updateFormat(format: NowPlayingFormat?) {
        this.format = format ?: NowPlayingFormat.DISABLED
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        receiver.subscribe { newTrackInfo: MusicEvent ->
            Timber.i("$type: Received music update")
            if (trackInfo != newTrackInfo) {
                trackInfo = newTrackInfo

                mainScope.launch {
                    if (!isUpdating) {
                        updateNowPlaying()
                    } else {
                        shouldUpdate = true
                    }
                }
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        receiver.unsubscribe()
        mainScope.cancel()
    }

    private suspend fun updateNowPlaying() {
        isUpdating = true
        shouldUpdate = false

        val newText = formatNowPlaying(trackInfo)
        val textChanged = text?.toString() != newText
        val isShowing = isVisible && alpha > 0f

        if (textChanged) {
            // Only animate if text is actually changing
            if (isShowing && newText.isNotBlank()) {
                // Text is changing to a different non-empty value
                crossFadeText(newText)
            } else if (isShowing && newText.isBlank()) {
                // Text is disappearing
                fadeOutAndHide()
            } else if (!isShowing && newText.isNotBlank()) {
                // Text is appearing
                showAndFadeIn(newText)
            } else {
                // Empty to empty, no visible change needed
                text = newText
            }
        }

        isUpdating = false
        if (shouldUpdate) {
            updateNowPlaying()
        }
    }

    private suspend fun crossFadeText(newText: String) {
        // First transition: fade out
        animateLayoutChange {
            alpha = 0f
        }

        // Update text while invisible
        text = newText

        // Second transition: fade back in
        animateLayoutChange {
            alpha = 1f
        }
    }

    private suspend fun fadeOutAndHide() {
        // Single transition: fade out and hide
        animateLayoutChange {
            alpha = 0f
            visibility = GONE
        }

        // Clear text after animation
        text = ""
    }

    private suspend fun showAndFadeIn(newText: String) {
        // Set text and make visible but transparent
        text = newText
        visibility = VISIBLE
        alpha = 0f

        // Single transition: fade in
        animateLayoutChange {
            alpha = 1f
        }
    }

    private suspend fun animateLayoutChange(changes: (() -> Unit)? = null) {
        val parentLayout = parent as? ConstraintLayout ?: return

        TransitionManager.beginDelayedTransition(
            parentLayout,
            TransitionSet().apply {
                ordering = TransitionSet.ORDERING_TOGETHER
                addTransition(ChangeBounds())
                addTransition(Fade())
                duration = animationDuration
            },
        )

        changes?.invoke()
        delay(animationDuration)
    }

    private fun formatNowPlaying(trackInfo: MusicEvent): String {
        val (artist, song) = trackInfo
        return when (format) {
            NowPlayingFormat.SONG_ARTIST ->
                if (song.isNotBlank() && artist.isNotBlank()) {
                    "$song · $artist"
                } else {
                    song.takeIf { it.isNotBlank() } ?: artist
                }
            NowPlayingFormat.ARTIST_SONG ->
                if (artist.isNotBlank() && song.isNotBlank()) {
                    "$artist · $song"
                } else {
                    artist.takeIf { it.isNotBlank() } ?: song
                }
            NowPlayingFormat.ARTIST -> artist
            NowPlayingFormat.SONG -> song
            else -> ""
        }
    }
}
