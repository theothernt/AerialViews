@file:Suppress("unused", "RedundantOverride", "RedundantOverride", "EmptyMethod",
    "RedundantSuppression", "RedundantSuppression"
)

package com.neilturner.aerialviews.ui.screensaver

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.service.dreams.DreamService
import android.view.KeyEvent
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.utils.LocaleHelper
import com.neilturner.aerialviews.utils.WindowHelper
import java.util.Locale

class DreamActivity : DreamService() {
    private lateinit var videoController: VideoController

    @SuppressLint("AppBundleLocaleChanges")
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Log.i(TAG, "onAttachedToWindow")
        // Setup
        isFullscreen = true
        isInteractive = true

        // Start playback, etc
        videoController = if (!GeneralPrefs.localeScreensaver.startsWith("default")) {
            val locale = LocaleHelper.localeFromString(GeneralPrefs.localeScreensaver)

            if (GeneralPrefs.clockForceLatinDigits) {
                Locale.setDefault(Locale.UK)
            } else {
                Locale.setDefault(locale)
            }

            val config = Configuration(this.resources.configuration)
            config.setLocale(locale)
            val context = createConfigurationContext(config)
            // Log.i(TAG, "Locale: ${GeneralPrefs.localeScreensaver}")
            VideoController(context)
        } else {
            VideoController(this)
        }
        setContentView(videoController.view)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        // LoggingHelper.logScreenView("Screensaver", TAG)
        // Start playback, etc
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_UP && this::videoController.isInitialized) {
            // Log.i(TAG, "${event.keyCode}")

            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_DOWN_LEFT,
                KeyEvent.KEYCODE_DPAD_UP_LEFT,
                KeyEvent.KEYCODE_DPAD_DOWN_RIGHT,
                KeyEvent.KEYCODE_DPAD_UP_RIGHT -> return true

                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
                KeyEvent.KEYCODE_MEDIA_REWIND,
                KeyEvent.KEYCODE_MEDIA_PLAY,
                KeyEvent.KEYCODE_MEDIA_PAUSE,
                KeyEvent.KEYCODE_MEDIA_NEXT,
                KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                    // Should play/pause/rewind keys be passed
                    // to the background app or not
                    return if (GeneralPrefs.enableMediaButtonPassthrough) {
                        super.dispatchKeyEvent(event)
                    } else {
                        wakeUp()
                        true
                    }
                }

                KeyEvent.KEYCODE_DPAD_CENTER -> {
                    // Only disable OK button if left/right/up/down keys are in use
                    // to avoid accidental presses
                    if (GeneralPrefs.enablePlaybackSpeedChange ||
                        GeneralPrefs.enableSkipVideos
                    ) {
                        return true
                    }
                    wakeUp()
                    return true
                }

                KeyEvent.KEYCODE_DPAD_UP -> {
                    if (!GeneralPrefs.enablePlaybackSpeedChange) {
                        wakeUp()
                        return true
                    }
                    videoController.increaseSpeed()
                    return true
                }

                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (!GeneralPrefs.enablePlaybackSpeedChange) {
                        wakeUp()
                        return true
                    }
                    videoController.decreaseSpeed()
                    return true
                }

                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (!GeneralPrefs.enableSkipVideos) {
                        wakeUp()
                        return true
                    }
                    videoController.skipVideo(true)
                    return true
                }

                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (!GeneralPrefs.enableSkipVideos) {
                        wakeUp()
                        return true
                    }
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
        if (hasFocus && this::videoController.isInitialized) {
            WindowHelper.hideSystemUI(window, videoController.view)
        }
    }

    override fun onDreamingStopped() {
        super.onDreamingStopped()
        // Log.i(TAG, "onDreamingStopped")
        // Stop playback, animations, etc
        if (this::videoController.isInitialized) {
            videoController.stop()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // Log.i(TAG, "onDetachedFromWindow")
        // Remove resources
    }

    companion object {
        private const val TAG = "DreamActivity"
    }
}
