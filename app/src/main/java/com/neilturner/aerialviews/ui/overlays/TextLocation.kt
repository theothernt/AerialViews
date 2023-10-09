package com.neilturner.aerialviews.ui.overlays

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.widget.AppCompatTextView
import com.neilturner.aerialviews.models.enums.LocationType
import com.neilturner.aerialviews.models.prefs.InterfacePrefs
import com.neilturner.aerialviews.models.videos.AerialVideo

class TextLocation : AppCompatTextView {
    // replace with https://juliensalvi.medium.com/safe-delay-in-android-views-goodbye-handlers-hello-coroutines-cd47f53f0fbf
    private var currentPositionProgressHandler: (() -> Unit)? = null
    private val textAlpha = 1f // start + end values?
    private var canSkip = false

    // video current position

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        currentPositionProgressHandler = null
    }

    private fun loadVideo(video: AerialVideo) {
        // 1. If POI, set POI text, if empty use location, or else use location
        this.text = if (InterfacePrefs.locationStyle == LocationType.POI) {
            video.poi[0]?.replace("\n", " ") ?: video.location
        } else {
            video.location
        }

        // 2. Hide TextView if POI/location text is blank, or Location is set to off
        if (this.text.isBlank()) {
            this.visibility = View.GONE
        } else if (InterfacePrefs.locationStyle != LocationType.OFF) {
            this.visibility = View.VISIBLE
        }

        // 3. If set to POI, set timer to update text when interval is reached
        if (InterfacePrefs.locationStyle == LocationType.POI && video.poi.size > 1) { // everything else is static anyways
            val poiTimes = video.poi.keys.sorted() // sort ahead of time?
            var lastPoi = 0

            currentPositionProgressHandler = {
                // Find POI string at current position/time
                val time = 230000 / 1000 // player current position
                val poi = poiTimes.findLast { it <= time } ?: 0
                val update = poi != lastPoi

                // If new string and not fading in/out + loading new video
                if (update && canSkip) {
                    // Set new string and fade in
                    lastPoi = poi
                    this.animate().alpha(0f).setDuration(1000).withEndAction {
                        this.text = video.poi[poi]?.replace("\n", " ")
                        this.animate().alpha(textAlpha).setDuration(1000).start()
                    }.start()
                }

                // Set new interval for POI string update
                // Longer is a new string has just been set
                val interval = if (update) 3000 else 1000 // Small change to make ktlint happy
                this.postDelayed({
                    currentPositionProgressHandler?.let { it() }
                }, interval.toLong())
            }

            // Setup handler for initial run of this video
            this.postDelayed({
                currentPositionProgressHandler?.let { it() }
            }, 1000)
        } else {
            // POI is off or empty, so disable handler
            currentPositionProgressHandler = null
        }

        // 4. Is a location is visible and text is LTR (eg. Arabic), set text direction
        // May not be needed as text alignment can be used?
//        if (InterfacePrefs.locationStyle != LocationType.OFF &&
//            this.text.isNotBlank()
//        ) {
//            if (LocaleHelper.isLtrText(this.text.toStringOrEmpty())) {
//                videoBinding.clock.textDirection = View.TEXT_DIRECTION_LTR
//            } else {
//                videoBinding.clock.textDirection = View.TEXT_DIRECTION_LOCALE
//            }
//        }
    }

    companion object {
        private const val TAG = "TextLocation"
    }
}
