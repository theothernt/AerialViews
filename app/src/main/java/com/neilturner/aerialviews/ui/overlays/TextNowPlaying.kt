package com.neilturner.aerialviews.ui.overlays

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
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

class TextNowPlaying : AppCompatTextView {
    var type = OverlayType.MUSIC1
    var format = NowPlayingFormat.DISALBED

    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private val receiver = EventsReceiver()

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    init {
        TextViewCompat.setTextAppearance(this, R.style.OverlayText)
    }

    fun updateFormat(format: NowPlayingFormat?) {
        this.format = format ?: NowPlayingFormat.DISALBED
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        receiver.subscribe<MusicEvent> {
            updateNowPlaying(it)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        receiver.unsubscribe()
        coroutineScope.cancel()
    }

    private fun updateNowPlaying(trackInfo: MusicEvent) = coroutineScope.launch {
        animate().alpha(0f).setDuration(300)
        delay(300)
        text = formatNowPlaying(trackInfo)
        animate().alpha(1f).setDuration(300)
    }

    private fun formatNowPlaying(trackInfo: MusicEvent): String {
        val (artist, song) = trackInfo
        return when (format) {
            NowPlayingFormat.SONG_ARTIST -> "$song · $artist"
            NowPlayingFormat.ARTIST_SONG -> "$artist · $song"
            NowPlayingFormat.ARTIST -> artist
            NowPlayingFormat.SONG -> song
            else -> ""
        }
    }

    companion object {
        private const val TAG = "TextNowPlaying"
    }
}
