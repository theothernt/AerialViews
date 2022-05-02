@file:Suppress("unused")

package com.neilturner.aerialviews.ui.screensaver

import android.service.dreams.DreamService
import android.util.Log
import android.view.KeyEvent
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.utils.WindowHelper

class DreamActivity : DreamService() {
    private lateinit var videoController: VideoController

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        Log.i(TAG, "onAttachedToWindow")
        // Setup
        isFullscreen = true
        isInteractive = true
        videoController = VideoController(this)
        setContentView(videoController.view)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        // Start playback, etc
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_UP) {
            // Log.i(TAG, "${event.keyCode}")

            if (!GeneralPrefs.enableSkipVideos)
                wakeUp()

            when (event.keyCode) {
                // Capture all d-pad presses for future use
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_DPAD_DOWN_LEFT,
                KeyEvent.KEYCODE_DPAD_UP_LEFT,
                KeyEvent.KEYCODE_DPAD_DOWN_RIGHT,
                KeyEvent.KEYCODE_DPAD_UP_RIGHT,
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN -> return true

                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    videoController.skipVideo(true)
                    return true
                }

                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    videoController.skipVideo()
                    return true
                }

                // Any other button press will close the screensaver
                else -> wakeUp()
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            WindowHelper.hideSystemUI(window, videoController.view)
        }
    }

    override fun onDreamingStopped() {
        super.onDreamingStopped()
        Log.i(TAG, "onDreamingStopped")
        // Stop playback, animations, etc
        if (this::videoController.isInitialized) {
            videoController.stop()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        Log.i(TAG, "onDetachedFromWindow")
        // Remove resources
    }

    companion object {
        private const val TAG = "DreamActivity"
    }
}
