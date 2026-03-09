package com.neilturner.aerialviews.ui.overlays

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.widget.TextViewCompat
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.MetadataType
import com.neilturner.aerialviews.models.enums.OverlayType
import com.neilturner.aerialviews.ui.core.VideoPlayerView
import com.neilturner.aerialviews.ui.overlays.state.MetadataOverlayState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MetadataOverlay : AppCompatTextView {
    // replace with https://juliensalvi.medium.com/safe-delay-in-android-views-goodbye-handlers-hello-coroutines-cd47f53f0fbf
    private var poiJob: Job? = null
    private val textAlpha = 1f // start + end values?
    private val minVisibleAlphaForPoiFade = 0.95f
    var isFadingOutMedia = false // Stops POI change + fade as video is ending
    var type: OverlayType = OverlayType.METADATA1

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    init {
        TextViewCompat.setTextAppearance(this, R.style.OverlayText)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        poiJob?.cancel()
        poiJob = null
    }

    fun updateLocationData(
        location: String,
        poi: Map<Int, String>,
        metadataType: MetadataType,
        player: VideoPlayerView,
    ) {
        isFadingOutMedia = false
        this.visibility = VISIBLE

        // If POI, set POI text, if empty use location, or else use location
        this.text =
            if (metadataType == MetadataType.DYNAMIC) {
                poi[0]?.replace("\n", " ") ?: location
            } else {
                location
            }

        // Hide TextView if POI/location text is blank
        if (this.text.isBlank()) {
            this.visibility = GONE
        }

        // If set to POI, set timer to update text when interval is reached
        if (metadataType == MetadataType.DYNAMIC && poi.size > 1) { // everything else is static anyways
            updatePointsOfInterest(poi, player)
        } else {
            // POI is off or empty, so disable handler
            poiJob?.cancel()
            poiJob = null
        }
    }

    fun render(
        state: MetadataOverlayState,
        player: VideoPlayerView,
    ) {
        updateLocationData(state.text, state.poi, state.metadataType, player)
    }

    private fun updatePointsOfInterest(
        poi: Map<Int, String>,
        player: VideoPlayerView,
    ) {
        val poiTimes = poi.keys.sorted() // sort ahead of time?
        var lastPoi = 0

        poiJob?.cancel()
        poiJob =
            findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
                delay(1000)
                while (isActive) {
                    // Find POI string at current position/time
                    val time = player.currentPosition / 1000 // player current position
                    val newPoi = poiTimes.findLast { it <= time } ?: 0
                    val shouldUpdate = newPoi != lastPoi

                    // If new string and not fading in/out + loading new video
                    if (shouldUpdate && !isFadingOutMedia) {
                        val nextText = poi[newPoi]?.replace("\n", " ") ?: ""
                        lastPoi = newPoi // Compiler bug?

                        // If auto-hide has already faded this overlay out, update text silently.
                        // Do not animate alpha back to 1f.
                        if (this@MetadataOverlay.alpha < minVisibleAlphaForPoiFade) {
                            this@MetadataOverlay.text = nextText
                            delay(1000)
                            continue
                        }

                        // Set new string and fade in
                        this@MetadataOverlay
                            .animate()
                            .alpha(0f)
                            .setDuration(1000)
                            .withEndAction {
                                this@MetadataOverlay.text = nextText
                                this@MetadataOverlay
                                    .animate()
                                    .alpha(textAlpha)
                                    .setDuration(1000)
                                    .start()
                            }.start()
                    }

                    // Set new interval for POI string update
                    // Longer is a new string has just been set
                    val interval = if (shouldUpdate) 3000L else 1000L
                    delay(interval)
                }
            }
    }
}
