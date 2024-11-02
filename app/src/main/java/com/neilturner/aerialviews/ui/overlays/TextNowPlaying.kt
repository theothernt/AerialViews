package com.neilturner.aerialviews.ui.overlays

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.widget.TextViewCompat
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.NowPlayingFormat
import com.neilturner.aerialviews.models.enums.OverlayType
import com.neilturner.aerialviews.services.MusicEvent
import me.kosert.flowbus.EventsReceiver
import me.kosert.flowbus.subscribe

class TextNowPlaying : AppCompatTextView {
    var type = OverlayType.MUSIC1
    var format = NowPlayingFormat.DISABLED

    private val receiver = EventsReceiver()
    private var trackInfo = MusicEvent()
    private var shouldUpdate = false
    private var isUpdating = false

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    init {
        TextViewCompat.setTextAppearance(this, R.style.OverlayText)
    }

    fun updateFormat(format: NowPlayingFormat?) {
        this.format = format ?: NowPlayingFormat.DISABLED
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        receiver.subscribe { newTrackInfo: MusicEvent ->
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

    private fun updateNowPlaying() {
        animate().alpha(0f).duration = 400
        shouldUpdate = false
        text = formatNowPlaying(trackInfo)
        animate().alpha(1f).duration = 400

        if (shouldUpdate) {
            updateNowPlaying()
        }
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
