package com.neilturner.aerialviews.ui.screensaver

import android.service.dreams.DreamService
import android.view.KeyEvent
import com.neilturner.aerialviews.utils.WindowHelper

class DreamActivity : DreamService() {
    private var videoController: VideoController? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        isFullscreen = true
        isInteractive = true
        videoController = VideoController(this)

        val view = videoController!!.view
        setContentView(view)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_UP &&
                event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            videoController!!.skipVideo()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            WindowHelper.hideSystemUI(window, videoController!!.view)
        }
    }

    override fun onDreamingStopped() {
        videoController!!.stop()
        super.onDreamingStopped()
    }
}