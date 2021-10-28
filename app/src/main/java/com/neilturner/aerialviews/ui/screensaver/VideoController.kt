package com.neilturner.aerialviews.ui.screensaver

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.Animation.AnimationListener
import androidx.databinding.DataBindingUtil
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.databinding.AerialActivityBinding
import com.neilturner.aerialviews.databinding.VideoViewBinding
import com.neilturner.aerialviews.models.VideoPlaylist
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.models.videos.AerialVideo
import com.neilturner.aerialviews.services.VideoService
import com.neilturner.aerialviews.ui.screensaver.ExoPlayerView.OnPlayerEventListener
import kotlinx.coroutines.runBlocking

class VideoController(context: Context) : OnPlayerEventListener {
    private val binding: AerialActivityBinding
    private var playlist: VideoPlaylist? = null
    private var canSkip: Boolean
    private var alternateText: Boolean
    private var previousVideo: Boolean

    init {
        val inflater = LayoutInflater.from(context)
        binding = DataBindingUtil.inflate(inflater, R.layout.aerial_activity, null, false)

        val showClock = GeneralPrefs.showClock
        binding.showLocation = GeneralPrefs.showLocation
        alternateText = GeneralPrefs.alternateTextPosition

        if (showClock) {
            binding.showClock = !alternateText
            binding.showAltClock = alternateText
        } else {
            binding.showClock = showClock
            binding.showAltClock = showClock
        }

        binding.videoView0.controller = binding.videoView0.videoView
        binding.videoView0.videoView.setOnPlayerListener(this)

        val service = VideoService(context)
        runBlocking { playlist = service.fetchVideos() }

        canSkip = true
        previousVideo = false

        binding.root.post { start() }
    }

    val view: View
        get() = binding.root

    private fun start() {
        loadVideo(binding.videoView0, playlist!!.nextVideo())
    }

    fun stop() {
        binding.videoView0.videoView.release()
    }

    fun skipVideo(previous: Boolean = false) {
        previousVideo = previous
        fadeOutCurrentVideo()
    }

    private fun fadeOutCurrentVideo() {
        if (!canSkip) return
        canSkip = false
        val animation: Animation = AlphaAnimation(0f, 1f)
        animation.duration = ExoPlayerView.DURATION
        animation.setAnimationListener(object : AnimationListener {
            override fun onAnimationStart(animation: Animation) {}
            override fun onAnimationEnd(animation: Animation) {
                binding.loadingView.visibility = View.VISIBLE

                val video = if (!previousVideo) {
                    playlist!!.nextVideo()
                } else {
                    playlist!!.previousVideo()
                }

                loadVideo(binding.videoView0, video)

                if (alternateText) {
                    binding.altTextPosition = !binding.altTextPosition
                }
            }
            override fun onAnimationRepeat(animation: Animation) {}
        })
        binding.loadingView.startAnimation(animation)
    }

    private fun fadeInNextVideo() {
        if (binding.loadingView.visibility == View.VISIBLE) {
            val animation: Animation = AlphaAnimation(1f, 0f)
            animation.duration = ExoPlayerView.DURATION
            animation.setAnimationListener(object : AnimationListener {
                override fun onAnimationStart(animation: Animation) {}
                override fun onAnimationEnd(animation: Animation) {
                    binding.loadingView.visibility = View.GONE
                    canSkip = true
                }
                override fun onAnimationRepeat(animation: Animation) {}
            })
            binding.loadingView.startAnimation(animation)
        }
    }

    private fun loadVideo(videoBinding: VideoViewBinding, video: AerialVideo) {
        Log.i("LoadVideo", "Playing: ${video.location} - ${video.uri}")
        videoBinding.location.text = video.location
        videoBinding.videoView.setUri(video.uri)
        videoBinding.videoView.start()
    }

    override fun onPrepared(view: ExoPlayerView?) {
        fadeInNextVideo()
    }

    override fun onAlmostFinished(view: ExoPlayerView?) {
        fadeOutCurrentVideo()
    }

    override fun onError(view: ExoPlayerView?) {
        binding.root.post { start() }
    }
}