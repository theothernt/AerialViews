@file:Suppress("unused")

package com.neilturner.aerialviews.ui.overlays

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.widget.TextViewCompat
import com.neilturner.aerialviews.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

class TextNowPlaying : AppCompatTextView {

    var nowPlaying: SharedFlow<String>? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    init {
        TextViewCompat.setTextAppearance(this, R.style.OverlayText)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        coroutineScope.launch {
            updateNowPlaying()
        }
    }

    @OptIn(FlowPreview::class)
    private suspend fun updateNowPlaying() {
        nowPlaying
            ?.distinctUntilChanged()
            ?.sample(700)
            ?.collectLatest {
                animate().alpha(0f).setDuration(300)
                delay(300)
                text = it
                animate().alpha(1f).setDuration(300)
        }
    }

    companion object {
        private const val TAG = "TextNowPlaying"
    }
}
