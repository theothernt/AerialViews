package com.neilturner.aerialviews.utils

import android.annotation.SuppressLint
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

class RefreshRateHelper(
    private val context: Context,
) {
    private val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    private val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY)

    fun setRefreshRate(fps: Float?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            Timber.e("Android TV version too old for AFR...")
            return
        }

        if (fps == null || fps == 0f) {
            Timber.e("Unable to get video frame rate...")
            return
        }

        // Store original mode if not already saved
        if (originalMode == null) {
            Timber.i("Saving original mode for later: ${display.mode.modeId}")
            originalMode = display.mode
        }

        val sortedModes = display.supportedModes.sortedBy { it.refreshRate }
        val supportedModes = getModesForResolution(sortedModes, display.mode)

        // 1. Match FPS with HZ exactly if possible eg. 29.97fps to 29.97Hz
        // 2. Less accurate matches eg. 29.97fps to 30fps to 60Hz
        val targetRefreshRate =
            when {
                abs(fps - 24f) < 0.1f -> 24f
                abs(fps - 25f) < 0.1f -> 25f
                abs(fps - 30f) < 0.1f -> 30f
                else -> return // Don't change for other framerates
            }

        // Find the best matching mode
        val bestMode =
            supportedModes.maxByOrNull { mode ->
                val score = calculateModeScore(mode, targetRefreshRate)
                score
            }

        Timber.i("Refresh rate chosen: ${bestMode?.refreshRate} (Mode: ${bestMode?.modeId})")
        bestMode?.let { mode ->
            changeRefreshRate(context, mode)
        }
    }

    private fun logRefreshRate(event: String, displayId: Int) {
        val display = displayManager.getDisplay(displayId)
        val refreshRate = display.refreshRate

        Timber.i("$event: Display ID = $displayId, Refresh Rate = ${refreshRate}Hz")
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun getModesForResolution(
        supportedModes: List<Display.Mode>,
        activeMode: Display.Mode,
    ): List<Display.Mode> {
        val filteredModes = mutableListOf<Display.Mode>()

        for (mode in supportedModes) {
            if (mode.physicalWidth == activeMode.physicalWidth &&
                mode.physicalHeight == activeMode.physicalHeight
            ) {
                filteredModes.add(mode)
            }
        }

        return filteredModes
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun calculateModeScore(
        mode: Display.Mode,
        targetRefreshRate: Float,
    ): Float {
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
        @SuppressLint("StaticFieldLeak")
        private var overlayView: View? = null
        private var originalMode: Display.Mode? = null
        private var windowManager: WindowManager? = null

        fun restoreOriginalMode(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                return
            }

            originalMode
                ?.let { mode ->
                    Timber.d("Restoring original mode: ${mode.modeId}")
                    changeRefreshRate(context, mode)
                }.apply {
                    windowManager?.removeView(overlayView)
                    windowManager = null
                    originalMode = null
                    overlayView = null
                }
        }

        @RequiresApi(Build.VERSION_CODES.M)
        private fun changeRefreshRate(context: Context, mode: Display.Mode) {
            if (context !is Activity) {
                useOverlay(context, mode.modeId)
            } else {
                useWindow(context, mode.modeId)
            }
        }

        @RequiresApi(Build.VERSION_CODES.M)
        private fun useWindow(
            context: Context,
            mode: Int,
        ) {
            Timber.d("Using Activity/Window...")
            val window = (context as Activity).window
            val layoutParams = window.attributes
            layoutParams.preferredDisplayModeId = mode
            window.attributes = layoutParams
        }

        @RequiresApi(Build.VERSION_CODES.M)
        private fun useOverlay(
            context: Context,
            mode: Int,
        ) {
            // View must be added/removed for refresh rate to change reliably
            if (overlayView == null) {
                Timber.i("Using NEW Overlay view...")
                windowManager = context.getSystemService(WINDOW_SERVICE) as WindowManager
                overlayView = View(context)
                overlayView?.setBackgroundColor(Color.argb(0, 0, 0, 0))
                val params = buildViewParams()
                windowManager?.addView(overlayView, params)
            } else {
                Timber.i("Using EXISTING Overlay view...")
            }
            windowManager?.removeView(overlayView)
            overlayView = View(context)
            overlayView?.setBackgroundColor(Color.argb(0, 0, 0, 0))
            val params = buildViewParams().apply { preferredDisplayModeId = mode }
            windowManager?.addView(overlayView, params)
        }

        private fun buildViewParams(): WindowManager.LayoutParams {
            val dimension = WindowManager.LayoutParams.MATCH_PARENT
            val pixelFormat = PixelFormat.TRANSLUCENT

            val overlayType =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY
                }

            return WindowManager.LayoutParams(
                dimension,
                dimension,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                pixelFormat,
            )
        }
    }
}
