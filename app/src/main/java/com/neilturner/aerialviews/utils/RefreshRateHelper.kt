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
import com.neilturner.aerialviews.BuildConfig
import timber.log.Timber

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

        val sortedModes = display.supportedModes.sortedBy { it.refreshRate }
        if (sortedModes.size <= 1 && !BuildConfig.DEBUG) {
            Timber.i("Only 1 mode found, exiting...")
            return
        }

        val supportedModes = getModesForResolution(sortedModes, display.mode)
        Timber.i("Suitable modes for current resolution: ${supportedModes.size} (Total: ${sortedModes.size})")

        val availableRefreshRates =
            supportedModes.joinToString(", ") { it.refreshRate.roundTo(2).toString() + "Hz" }
        Timber.i("Available Refresh Rates: $availableRefreshRates")

        // Store original mode if not already saved
        if (originalMode == null) {
            Timber.i("Saving original mode for later: ${display.mode.modeId}")
            originalMode = display.mode
        }

        val usePreciseMode = false
        val preciseModes =
            mapOf(
                23.98f to 23.98f,
                24.0f to 24.0f,
                25.0f to 50.0f, // No 25Hz mode
                29.97f to 29.97f,
                30.0f to 30.0f,
                50.0f to 50.0f,
                59.94f to 59.94f,
                60.0f to 60.0f,
            )

        val impreciseModes =
            mapOf(
                23.98f to listOf(24.0f, 23.98f),
                24.0f to listOf(24.0f, 23.98f),
                25.0f to listOf(50.0f),
                29.97f to listOf(60.0f, 59.97f),
                30.0f to listOf(60.0f, 59.97f),
                50.0f to listOf(50.0f),
                59.94f to listOf(60.0f, 59.97f),
                60.0f to listOf(60.0f, 59.97f),
            )

        Timber.i("Video fps: ${fps.roundTo(2)}")
        val frameRates = impreciseModes[fps.roundTo(2)]
        if (frameRates?.contains(display.mode.refreshRate) == false) {
            val target = frameRates.first()
            sortedModes
                .firstOrNull { it.refreshRate.roundTo(2) == target }
                ?.let { bestMode ->
                    Timber.i("Best mode found: ${bestMode.refreshRate.roundTo(2)}Hz")
                    changeRefreshRate(context, bestMode)
                }
        }
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
        private fun changeRefreshRate(
            context: Context,
            mode: Display.Mode,
        ) {
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
