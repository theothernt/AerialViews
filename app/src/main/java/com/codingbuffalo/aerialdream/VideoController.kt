package com.codingbuffalo.aerialdream

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.Animation.AnimationListener
import androidx.databinding.DataBindingUtil
import androidx.preference.PreferenceManager
import com.codingbuffalo.aerialdream.ExoPlayerView.OnPlayerEventListener
import com.codingbuffalo.aerialdream.data.Video
import com.codingbuffalo.aerialdream.data.VideoInteractor
import com.codingbuffalo.aerialdream.data.VideoPlaylist
import com.codingbuffalo.aerialdream.databinding.AerialDreamBinding
import com.codingbuffalo.aerialdream.databinding.VideoViewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class VideoController(context: Context?) : OnPlayerEventListener {
    private val binding: AerialDreamBinding
    private var playlist: VideoPlaylist? = null
    private val videoType2019: String?
    private var canSkip: Boolean
    private val videoSource: Int
    private var alternateText: Boolean

    init {
        val inflater = LayoutInflater.from(context)
        binding = DataBindingUtil.inflate(inflater, R.layout.aerial_dream, null, false)

        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val uiPrefs = prefs.getStringSet("ui_options", null)

        var showClock = true
        var showLocation = true
        alternateText = false

        if (!uiPrefs!!.contains("0")) showClock = false
        if (!uiPrefs.contains("1")) showLocation = false
        if (uiPrefs.contains("3")) alternateText = true

        videoType2019 = prefs.getString("source_apple_2019", "1080_h264")
        videoSource = prefs.getString("video_source", "0")!!.toInt()
        binding.showLocation = showLocation

        if (showClock) {
            binding.showClock = !alternateText
            binding.showAltClock = alternateText
        } else {
            binding.showClock = showClock
            binding.showAltClock = showClock
        }

        binding.videoView0.controller = binding.videoView0.videoView
        binding.videoView0.videoView.setOnPlayerListener(this)

        runBlocking {
            withContext(Dispatchers.IO) {
                val interactor = VideoInteractor(
                        context!!,
                        videoSource,
                        videoType2019!!)
                playlist = interactor.fetchVideos()
                binding.root.post { start() }
            }
        }
        canSkip = true
    }

    val view: View
        get() = binding.root

    private fun start() {
        loadVideo(binding.videoView0, video)
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

    private fun loadVideo(videoBinding: VideoViewBinding, video: Video?) {
        Log.i("LoadVideo", "Playing: " + video!!.location + " - " + video.getUri(videoType2019!!))
        videoBinding.videoView.setUri(video.getUri(videoType2019))
        videoBinding.location.text = video.location
        videoBinding.videoView.start()
    }

    private val video: Video?
        get() = playlist!!.video

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