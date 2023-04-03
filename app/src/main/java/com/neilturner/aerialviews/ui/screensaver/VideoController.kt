package com.neilturner.aerialviews.ui.screensaver

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.widget.TextView
import android.widget.Toast
import androidx.databinding.DataBindingUtil
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.databinding.AerialActivityBinding
import com.neilturner.aerialviews.databinding.VideoViewBinding
import com.neilturner.aerialviews.models.LocationType
import com.neilturner.aerialviews.models.VideoPlaylist
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.models.prefs.InterfacePrefs
import com.neilturner.aerialviews.models.videos.AerialVideo
import com.neilturner.aerialviews.services.VideoService
import com.neilturner.aerialviews.ui.screensaver.ExoPlayerView.OnPlayerEventListener
import com.neilturner.aerialviews.utils.DeviceHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class VideoController(private val context: Context, private val window: Window) : OnPlayerEventListener {
    private var currentPositionProgressHandler: (() -> Unit)? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private lateinit var playlist: VideoPlaylist
    private lateinit var currentVideo: AerialVideo
    private val textAlpha = 1f
    private var previousVideo = false
    private var canSkip = false
    private val videoView: VideoViewBinding
    private val loadingView: View
    private var loadingText: TextView
    val view: View

    init {
        val inflater = LayoutInflater.from(context)
        val binding = DataBindingUtil.inflate(inflater, R.layout.aerial_activity, null, false) as AerialActivityBinding
        binding.interfacePrefs = InterfacePrefs
        binding.showLocation = InterfacePrefs.showLocation != LocationType.OFF
        binding.videoView0.videoView.setOnPlayerListener(this)

        videoView = binding.videoView0
        loadingView = binding.loadingView.root
        loadingText = binding.loadingView.loadingText
        view = binding.root

        val service = VideoService(context)
        coroutineScope.launch {
            playlist = service.fetchVideos()
            Log.i(TAG, "Playlist items: ${playlist.size}")
            if (playlist.size > 0) {
                loadVideo(videoView, playlist.nextVideo())
            } else {

                showLoadingError(context)
            }
        }

        if (DeviceHelper.isFireTV()) {
            val newColor = Color.parseColor("#e9e9e9")
            val newFont = Typeface.create("sans-serif-light", Typeface.NORMAL)

            loadingText.setTextColor(newColor)
            loadingText.typeface = newFont

            binding.videoView0.location.setTextColor(newColor)
            binding.videoView0.location.typeface = newFont
        }
    }

    fun stop() {
        currentPositionProgressHandler = null
        videoView.videoView.release()
    }

    fun skipVideo(previous: Boolean = false) {
        previousVideo = previous
        fadeOutCurrentVideo()
    }

    fun increaseSpeed() {
        videoView.videoView.increaseSpeed()
    }

    fun decreaseSpeed() {
        videoView.videoView.decreaseSpeed()
    }

    private fun showLoadingError(context: Context) {
        val res = context.resources!!
        loadingText.text = res.getString(R.string.loading_error)
    }

    private fun fadeOutLoading() {
        loadingText
            .animate()
            .alpha(0f)
            .setDuration(1000)
            .withEndAction {
                loadingText.visibility = TextView.GONE
            }.start()
    }

    private fun fadeOutCurrentVideo() {
        if (!canSkip) return
        canSkip = false

        loadingView
            .animate()
            .alpha(1f)
            .setDuration(ExoPlayerView.FADE_DURATION)
            .withStartAction {
                loadingView.visibility = View.VISIBLE
            }
            .withEndAction {
                currentPositionProgressHandler = null

                val video = if (!previousVideo) {
                    playlist.nextVideo()
                } else {
                    playlist.previousVideo()
                }
                previousVideo = false

                videoView.location.text = ""
                videoView.location.alpha = textAlpha
                loadVideo(videoView, video)

                if (InterfacePrefs.alternateTextPosition) {
                    videoView.isAlternateRun = !videoView.isAlternateRun
                }
            }.start()
    }

    private fun fadeInNextVideo() {
        if (loadingView.visibility == View.GONE) {
            return
        }

        if (loadingText.visibility == View.VISIBLE) {
            fadeOutLoading()
        }

        loadingView
            .animate()
            .alpha(0f)
            .setDuration(ExoPlayerView.FADE_DURATION)
            .withEndAction {
                loadingView.visibility = View.GONE
                canSkip = true
            }.start()
    }

    private fun loadVideo(videoBinding: VideoViewBinding, video: AerialVideo) {
        Log.i(TAG, "Playing: ${video.location} - ${video.uri} (${video.poi})")
        currentVideo = video
        videoBinding.location.text = if (InterfacePrefs.showLocation == LocationType.POI) {
            video.poi[0]?.replace("\n", " ") ?: video.location
        } else {
            video.location
        }

        if (videoBinding.location.text.isBlank()) {
            videoBinding.location.visibility = View.GONE
        } else if (InterfacePrefs.showLocation != LocationType.OFF) {
            videoBinding.location.visibility = View.VISIBLE
        }

        if (InterfacePrefs.showLocation == LocationType.POI && video.poi.size > 1) { // everything else is static anyways
            val poiTimes = video.poi.keys.sorted()
            var lastPoi = 0

            currentPositionProgressHandler = {
                val time = videoBinding.videoView.currentPosition / 1000
                val poi = poiTimes.findLast { it <= time } ?: 0
                val update = poi != lastPoi

                if (update && canSkip) {
                    lastPoi = poi
                    videoBinding.location.animate().alpha(0f).setDuration(1000).withEndAction {
                        videoBinding.location.text = video.poi[poi]?.replace("\n", " ")
                        videoBinding.location.animate().alpha(textAlpha).setDuration(1000).start()
                    }.start()
                }

                val interval = if (update) 3000 else 1000 // Small change to make ktlint happy
                videoBinding.location.postDelayed({
                    currentPositionProgressHandler?.let { it() }
                }, interval.toLong())
            }

            videoBinding.location.postDelayed({
                currentPositionProgressHandler?.let { it() }
            }, 1000)
        } else {
            currentPositionProgressHandler = null
        }

        videoBinding.videoView.setUri(video.uri)
        videoBinding.videoView.start()
    }

    override fun onPrepared() {
        fadeInNextVideo()
    }

    override fun onAlmostFinished() {
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
            loadVideo(videoView, playlist.nextVideo())
        } else {
            fadeOutCurrentVideo()
        }
    }

    companion object {
        private const val TAG = "VideoController"
    }
}
