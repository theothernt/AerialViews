@file:Suppress("unused")

package com.neilturner.aerialviews.ui.overlays

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.widget.TextViewCompat
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.NowPlayingFormat
import com.neilturner.aerialviews.models.enums.OverlayType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

class TextNowPlaying : AppCompatTextView {
    var type = OverlayType.MUSIC1
    var format = NowPlayingFormat.DISALBED

    var nowPlaying: SharedFlow<Pair<String, String>>? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

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
        coroutineScope.launch {
            updateNowPlaying()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        coroutineScope.cancel()
    }

    @OptIn(FlowPreview::class)
    private suspend fun updateNowPlaying() {
        nowPlaying
            ?.distinctUntilChanged()
            ?.sample(600)
            ?.collectLatest {
                animate().alpha(0f).setDuration(300)
                delay(300)
                text = formatNowPlaying(it)
                animate().alpha(1f).setDuration(300)
            }
    }

    private fun formatNowPlaying(trackInfo: Pair<String, String>): String {
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
