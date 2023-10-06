package com.neilturner.aerialviews.ui.screensaver

import android.content.Context
import android.graphics.Typeface
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.constraintlayout.helper.widget.Flow
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat.generateViewId
import androidx.databinding.DataBindingUtil
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.databinding.AerialActivityBinding
import com.neilturner.aerialviews.databinding.VideoViewBinding
import com.neilturner.aerialviews.models.VideoPlaylist
import com.neilturner.aerialviews.models.enums.OverlayType
import com.neilturner.aerialviews.models.enums.SlotType
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.models.prefs.InterfacePrefs
import com.neilturner.aerialviews.models.videos.AerialVideo
import com.neilturner.aerialviews.services.VideoService
import com.neilturner.aerialviews.ui.overlays.AltTextClock
import com.neilturner.aerialviews.ui.overlays.TextLocation
import com.neilturner.aerialviews.ui.screensaver.ExoPlayerView.OnPlayerEventListener
import com.neilturner.aerialviews.utils.FontHelper
import com.neilturner.aerialviews.utils.SlotHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class VideoController(private val context: Context) : OnPlayerEventListener {
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private lateinit var playlist: VideoPlaylist
    private var typeface: Typeface? = null

    private var previousVideo = false
    private var canSkip = false

    private val layout: ConstraintLayout
    private val videoView: VideoViewBinding
    private val loadingView: View
    private var loadingText: TextView
    private var player: ExoPlayerView
    private val flowBottomLeft: Flow
    private val flowBottomRight: Flow
    private val flowTopLeft: Flow
    private val flowTopRight: Flow
    val view: View

    init {
        val inflater = LayoutInflater.from(context)
        val binding = DataBindingUtil.inflate(inflater, R.layout.aerial_activity, null, false) as AerialActivityBinding

        view = binding.root
        loadingView = binding.loadingView.root
        loadingText = binding.loadingView.loadingText

        videoView = binding.videoView
        layout = videoView.layout
        player = videoView.player
        player.setOnPlayerListener(this)

        flowBottomLeft = videoView.flowBottomLeft
        flowBottomRight = videoView.flowBottomRight
        flowTopLeft = videoView.flowTopLeft
        flowTopRight = videoView.flowTopRight

        // Should try/catch etc
        // Take pref as param
        typeface = FontHelper.getTypeface(context)
        loadingText.typeface = typeface

        coroutineScope.launch {
            playlist = VideoService(context).fetchVideos()
            if (playlist.size > 0) {
                Log.i(TAG, "Playlist items: ${playlist.size}")
                loadVideo(playlist.nextVideo())
            } else {
                showLoadingError(context)
            }
        }

        // loop through overlay prefs and init chosen overlays + name into list
        // (after load video) clear slots
        // for each slot, find overlay name, find name in overlay list and add

        // 1. Load playlist
        // 2. load video, setup location/POI, start playback call
        // 3. playback started callback, fade out loading text, fade out loading view
        // 4. when video is almost finished - or skip - fade in loading view
        // 5. goto 2
    }

    private fun loadVideo(video: AerialVideo) {
        Log.i(TAG, "Playing: ${video.location} - ${video.uri} (${video.poi})")

        val view1 = getOverlayForSlot(SlotType.SLOT_BOTTOM_LEFT1)
        val view2 = getOverlayForSlot(SlotType.SLOT_BOTTOM_LEFT2)

        view1.id = generateViewId()
        view2.id = generateViewId()

        val view1Id = arrayOf(view1.id)
        val view2Id = arrayOf(view2.id)

        layout.addView(view1)
        layout.addView(view2)

        //layout.addView(view1)
        //flowBottomRight.addView(view1)
        //flowBottomRight.referencedIds = view1Id.toIntArray()
        flowBottomRight.referencedIds = view2Id.toIntArray() + view1Id.toIntArray()

        player.setUri(video.uri)
        player.start()
    }

    private fun getOverlayForSlot(slotType: SlotType): View {
        val prefs = SlotHelper.slotPrefs()
        val overlay = prefs.find { it.second == slotType }
        return getOverlay(overlay?.first ?: OverlayType.UNKNOWN)
    }

    private fun getOverlay(type: OverlayType): View {
        when (type) {
            OverlayType.CLOCK -> {
                val clock = AltTextClock(context)
                clock.typeface = typeface
                clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, InterfacePrefs.clockSize.toFloat())
                return clock
            }
            OverlayType.LOCATION -> {
                val location = TextLocation(context)
                location.typeface = typeface
                location.setTextSize(TypedValue.COMPLEX_UNIT_SP, InterfacePrefs.locationSize.toFloat())
                location.text = "Location view"
                return location
            }
            else -> {
                val emptyView = TextView(context)
                //emptyView.visibility = View.GONE
                emptyView.text = "Empty view"
                return emptyView
            }
        }
    }

    private fun fadeOutLoading() {
        // Fade out TextView
        loadingText
            .animate()
            .alpha(0f)
            .setDuration(1000)
            .withEndAction {
                loadingText.visibility = TextView.GONE
            }.start()
    }

    private fun fadeInNextVideo() {
        // LoadingView should always be hidden/gone
        // Remove?
        if (loadingView.visibility == View.GONE) {
            return
        }

        // If first video (ie. screensaver startup), fade out 'loading...' text
        if (loadingText.visibility == View.VISIBLE) {
            fadeOutLoading()
        }

        // Fade out LoadingView
        // Video should be playing underneath
        loadingView
            .animate()
            .alpha(0f)
            .setDuration(ExoPlayerView.FADE_DURATION)
            .withEndAction {
                loadingView.visibility = View.GONE
                canSkip = true
            }.start()
    }

    private fun fadeOutCurrentVideo() {
        if (!canSkip) return
        canSkip = false

        // Fade in LoadView (ie. black screen)
        loadingView
            .animate()
            .alpha(1f)
            .setDuration(ExoPlayerView.FADE_DURATION)
            .withStartAction {
                loadingView.visibility = View.VISIBLE
            }
            .withEndAction {
                // Pick next/previous video
                val video = if (!previousVideo) {
                    playlist.nextVideo()
                } else {
                    playlist.previousVideo()
                }
                previousVideo = false

                // Setting text + alpha on fade out
                // Should be moved?
                // videoView.location.text = ""
                // videoView.location.alpha = textAlpha

                loadVideo(video)

                // Change text alignment per video
                // Should be moved?
//                if (shouldAlternateTextPosition) {
//                    videoView.shouldAlternateTextPosition = !videoView.shouldAlternateTextPosition
//                }
            }.start()
    }

    private fun showLoadingError(context: Context) {
        val res = context.resources!!
        loadingText.text = res.getString(R.string.loading_error)
    }

    fun stop() {
        player.release()
    }

    fun skipVideo(previous: Boolean = false) {
        previousVideo = previous
        fadeOutCurrentVideo()
    }

    fun increaseSpeed() {
        player.increaseSpeed()
    }

    fun decreaseSpeed() {
        player.decreaseSpeed()
    }

    override fun onPrepared() {
        // Player has buffered video and has started playback
        fadeInNextVideo()
    }

    override fun onAlmostFinished() {
        // Player indicates video is nearly over
        fadeOutCurrentVideo()
    }

    override fun onPlaybackSpeedChanged() {
        val message = "Playback speed changed to: ${GeneralPrefs.playbackSpeed}x"
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    override fun onError() {
        // val message = "Error while trying to play ${currentVideo.uri}"
        // Toast.makeText(context, message, Toast.LENGTH_LONG).show()

        if (loadingView.visibility == View.VISIBLE) {
            loadVideo(playlist.nextVideo())
        } else {
            fadeOutCurrentVideo()
        }
    }

    companion object {
        private const val TAG = "VideoController"
    }
}
