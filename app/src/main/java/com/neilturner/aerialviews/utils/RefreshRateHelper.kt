package com.neilturner.aerialviews.utils

import android.app.Activity
import android.content.Context
import android.content.Context.WINDOW_SERVICE
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import android.view.View
import android.view.WindowManager
import androidx.annotation.RequiresApi
import timber.log.Timber
import kotlin.math.abs

class RefreshRateHelper(private val context: Context) {
    private val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    private val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY)

    fun setRefreshRate(fps: Float?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return
        }

        if (fps == null || fps == 0f) {
            Timber.i("Unable to get video frame rate...")
            return
        }

        // Store original mode if not already saved
        if (originalMode == null) {
            Timber.i("Saving original mode for later: ${display.mode.modeId}")
            originalMode = display.mode
        }

        val supportedModes = display.supportedModes
        val targetRefreshRate = when {
            abs(fps - 24f) < 0.1f -> 24f
            abs(fps - 25f) < 0.1f -> 25f
            abs(fps - 30f) < 0.1f -> 30f
            else -> return  // Don't change for other framerates
        }

        // Find the best matching mode
        val bestMode = supportedModes.maxByOrNull { mode ->
            val score = calculateModeScore(mode, targetRefreshRate)
            score
        }

        Timber.i("Refresh rate chosen: ${bestMode?.refreshRate} (Mode: ${bestMode?.modeId})")
        bestMode?.let { mode ->
            if (isDreamService(context)) {
                useOverlay(context, mode.modeId)
            } else {
                useWindow(context, mode.modeId)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun calculateModeScore(mode: Display.Mode, targetRefreshRate: Float): Float {
        val refreshRate = mode.refreshRate

        // Perfect match
        if (abs(refreshRate - targetRefreshRate) < 0.1f) return 100f

        // Multiple of target (e.g., 60Hz for 30fps)
        if (refreshRate % targetRefreshRate < 0.1f) return 80f

        // Otherwise, prefer closer rates but higher is better than lower
        return if (refreshRate > targetRefreshRate) {
            50f - abs(refreshRate - targetRefreshRate)
        } else {
            30f - abs(refreshRate - targetRefreshRate)
        }
    }

    companion object {
        private var originalMode: Display.Mode? = null
        private var overlayView: View? = null
        //private var windowManager: WindowManager? = null

        fun restoreOriginalMode(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                return
            }

            originalMode?.let { mode ->
                Timber.i("Restoring original mode: ${mode.modeId}")
                if (isDreamService(context)) {
                    useOverlay(context, mode.modeId)
                } else {
                    useWindow(context, mode.modeId)
                }
            }.apply {
                originalMode = null
                overlayView = null
            }
        }

        private fun isDreamService(context: Context): Boolean {
            return (context as Activity) == null
        }

        @RequiresApi(Build.VERSION_CODES.M)
        private fun useWindow(context: Context, mode: Int) {
            val window = (context as Activity).window
            val layoutParams = window.attributes
            layoutParams.preferredDisplayModeId = mode
            window.attributes = layoutParams
        }

        @RequiresApi(Build.VERSION_CODES.M)
        private fun useOverlay(context: Context, mode: Int) {
            overlayView = View(context)
            overlayView?.setBackgroundColor(Color.argb(0, 0, 0, 0))
            val dimension = WindowManager.LayoutParams.MATCH_PARENT
            val pixelFormat = PixelFormat.TRANSLUCENT // PixelFormat.RGBA_8888

            val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY
            }

            val params = WindowManager.LayoutParams(
                dimension, dimension,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                pixelFormat
            ).apply {
                preferredDisplayModeId = mode
                //screenOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            val windowManager = context.getSystemService(WINDOW_SERVICE) as WindowManager
            Timber.i("Adding overlay view")
            windowManager.addView(overlayView, params)
        }
    }
}