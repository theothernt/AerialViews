package com.neilturner.aerialviews.ui.overlays

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.widget.TextViewCompat
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.DescriptionManifestType
import com.neilturner.aerialviews.ui.core.VideoPlayerView

class LocationOverlay : AppCompatTextView {
    // replace with https://juliensalvi.medium.com/safe-delay-in-android-views-goodbye-handlers-hello-coroutines-cd47f53f0fbf
    private var currentPositionProgressHandler: (() -> Unit)? = null
    private val textAlpha = 1f // start + end values?
    var isFadingOutMedia = false // Stops POI change + fade as video is ending

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    init {
        TextViewCompat.setTextAppearance(this, R.style.OverlayText)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        currentPositionProgressHandler = null
    }

    fun updateLocationData(
        location: String,
        poi: Map<Int, String>,
        descriptionManifestType: DescriptionManifestType,
        player: VideoPlayerView,
    ) {
        isFadingOutMedia = false
        this.visibility = VISIBLE

        // If POI, set POI text, if empty use location, or else use location
        this.text =
            if (descriptionManifestType == DescriptionManifestType.POI) {
                poi[0]?.replace("\n", " ") ?: location
            } else {
                location
            }

        // Hide TextView if POI/location text is blank
        if (this.text.isBlank()) {
            this.visibility = GONE
        }

        // If set to POI, set timer to update text when interval is reached
        if (descriptionManifestType == DescriptionManifestType.POI && poi.size > 1) { // everything else is static anyways
            updatePointsOfInterest(poi, player)
        } else {
            // POI is off or empty, so disable handler
            currentPositionProgressHandler = null
        }
    }

    private fun updatePointsOfInterest(
        poi: Map<Int, String>,
        player: VideoPlayerView,
    ) {
        val poiTimes = poi.keys.sorted() // sort ahead of time?
        var lastPoi = 0

        currentPositionProgressHandler = {
            // Find POI string at current position/time
            val time = player.currentPosition / 1000 // player current position
            val newPoi = poiTimes.findLast { it <= time } ?: 0
            val shouldUpdate = newPoi != lastPoi

            // If new string and not fading in/out + loading new video
            if (shouldUpdate && !isFadingOutMedia) {
                // Set new string and fade in
                lastPoi = newPoi
                this
                    .animate()
                    .alpha(0f)
                    .setDuration(1000)
                    .withEndAction {
                        this.text = poi[newPoi]?.replace("\n", " ")
                        this
                            .animate()
                            .alpha(textAlpha)
                            .setDuration(1000)
                            .start()
                    }.start()
            }

            // Set new interval for POI string update
            // Longer is a new string has just been set
            val interval = if (shouldUpdate) 3000 else 1000
            this.postDelayed({ currentPositionProgressHandler?.let { it() } }, interval.toLong())
        }

        // Set initial delay for this method
        this.postDelayed({ currentPositionProgressHandler?.let { it() } }, 1000)
    }
}
