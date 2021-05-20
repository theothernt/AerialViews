package com.codingbuffalo.aerialdream.ui.screensaver

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.Animation.AnimationListener
import androidx.databinding.DataBindingUtil
import com.codingbuffalo.aerialdream.ui.screensaver.ExoPlayerView.OnPlayerEventListener
import com.codingbuffalo.aerialdream.R
import com.codingbuffalo.aerialdream.services.VideoService
import com.codingbuffalo.aerialdream.models.VideoPlaylist
import com.codingbuffalo.aerialdream.databinding.AerialDreamBinding
import com.codingbuffalo.aerialdream.databinding.VideoViewBinding
import com.codingbuffalo.aerialdream.models.prefs.GeneralPrefs
import com.codingbuffalo.aerialdream.models.videos.AerialVideo
import com.codingbuffalo.aerialdream.utils.FileHelper
import kotlinx.coroutines.runBlocking

class VideoController(context: Context) : OnPlayerEventListener {
    private val binding: AerialDreamBinding
    private var playlist: VideoPlaylist? = null
    private var canSkip: Boolean
    private var alternateText: Boolean

    init {
        val inflater = LayoutInflater.from(context)
        binding = DataBindingUtil.inflate(inflater, R.layout.aerial_dream, null, false)

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
        binding.root.post { start() }
        canSkip = true
    }

    val view: View
        get() = binding.root

    private fun start() {
        loadVideo(binding.videoView0, playlist!!.nextVideo())
    }

    fun stop() {
        binding.videoView0.videoView.release()
    }

    fun skipVideo() {
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
                loadVideo(binding.videoView0, playlist!!.nextVideo())
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

        val isLocalVideo = FileHelper.isLocalVideo(video.uri)
        val fileExists = FileHelper.fileExists(video.uri)

        Log.i("LoadVideo", "Local: $isLocalVideo, Exists: $fileExists")

        videoBinding.location.text = video.location
        videoBinding.videoView.setUri(video.uri, !isLocalVideo)
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