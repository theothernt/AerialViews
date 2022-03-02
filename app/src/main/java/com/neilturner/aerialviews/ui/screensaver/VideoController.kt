package com.neilturner.aerialviews.ui.screensaver

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import androidx.databinding.DataBindingUtil
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.databinding.AerialActivityBinding
import com.neilturner.aerialviews.databinding.VideoViewBinding
import com.neilturner.aerialviews.models.LocationStyle
import com.neilturner.aerialviews.models.VideoPlaylist
import com.neilturner.aerialviews.models.prefs.InterfacePrefs
import com.neilturner.aerialviews.models.videos.AerialVideo
import com.neilturner.aerialviews.services.VideoService
import com.neilturner.aerialviews.ui.screensaver.ExoPlayerView.OnPlayerEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class VideoController(context: Context) : OnPlayerEventListener {
    private var currentPositionProgressHandler: (() -> Unit)? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private val binding: AerialActivityBinding
    private var playlist: VideoPlaylist? = null
    private var canSkip = false
    private var previousVideo = false
    val view: View

    init {
        val inflater = LayoutInflater.from(context)
        binding = DataBindingUtil.inflate(inflater, R.layout.aerial_activity, null, false)
        binding.textPrefs = InterfacePrefs
        binding.videoView0.controller = binding.videoView0.videoView
        binding.videoView0.videoView.setOnPlayerListener(this)
        view = binding.root

        val service = VideoService(context)
        coroutineScope.launch {
            playlist = service.fetchVideos()
            loadVideo(binding.videoView0, playlist!!.nextVideo())
        }
    }

    fun stop() {
        currentPositionProgressHandler = null
        binding.videoView0.videoView.release()
    }

    fun skipVideo(previous: Boolean = false) {
        previousVideo = previous
        fadeOutCurrentVideo()
    }

    private fun fadeOutCurrentVideo() {
        if (!canSkip) return
        canSkip = false

        binding.loadingView
            .animate()
            .alpha(1f)
            .setDuration(ExoPlayerView.DURATION)
            .withStartAction {
                binding.loadingView.visibility = View.VISIBLE
            }
            .withEndAction {
                val video = if (!previousVideo) {
                    playlist!!.nextVideo()
                } else {
                    playlist!!.previousVideo()
                }
                previousVideo = false

                loadVideo(binding.videoView0, video)

                if (InterfacePrefs.alternateTextPosition) {
                    binding.videoView0.isAlternateRun = !binding.videoView0.isAlternateRun
                }
            }.start()
    }

    private fun fadeInNextVideo() {
        if (binding.loadingView.visibility == View.VISIBLE) {
            binding.loadingView
                .animate()
                .alpha(0f)
                .setDuration(ExoPlayerView.DURATION)
                .withEndAction {
                    binding.loadingView.visibility = View.GONE
                    canSkip = true
                }.start()
        }
    }

    private fun loadVideo(videoBinding: VideoViewBinding, video: AerialVideo) {
        Log.i(TAG, "Playing: ${video.location} - ${video.uri} (${video.poi})")
        videoBinding.location.text = if (InterfacePrefs.showLocationStyle == LocationStyle.VERBOSE) video.poi[0]?.replace("\n", " ") ?: video.location else video.location

        if (InterfacePrefs.showLocationStyle == LocationStyle.VERBOSE && video.poi.size > 1) { // everything else is static anyways
            val poiTimes = video.poi.keys.sorted()
            var lastPoi = 0

            currentPositionProgressHandler = {
                val time = videoBinding.videoView.currentPosition / 1000
                val poi = poiTimes.findLast { it <= time } ?: 0
                val update = poi != lastPoi

                if (update && canSkip) {
                    lastPoi = poi
                    videoBinding.location.animate().alpha(0f).setDuration(1000).withEndAction {
                        if (canSkip) {
                            videoBinding.location.text = video.poi[poi]?.replace("\n", " ")
                        }
                        videoBinding.location.animate().alpha(0.7f).setDuration(800).start()
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

    override fun onPrepared(view: ExoPlayerView?) {
        fadeInNextVideo()
    }

    override fun onAlmostFinished(view: ExoPlayerView?) {
        fadeOutCurrentVideo()
    }

    companion object {
        private const val TAG = "VideoController"
    }
}
