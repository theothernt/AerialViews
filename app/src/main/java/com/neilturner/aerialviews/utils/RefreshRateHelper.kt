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
import kotlin.math.ceil
import kotlin.math.roundToInt

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

        // 1. Match FPS with HZ exactly if possible eg. 29.97fps to 29.97Hz
        // 2. Less accurate matches eg. 29.97fps to 30fps to 60Hz
        // 23.97, 24.0, 29.97, 30.0, 50.0, 59.94, 60.0
        val targetRefreshRate = fps
        val usePreciseMode = false
        var bestMode: Display.Mode? = null

        bestMode =
            if (usePreciseMode) {
                pickPreciseMode(supportedModes, targetRefreshRate)
            } else {
                pickImpreciseMode(supportedModes, targetRefreshRate)
            }

        if (bestMode == null) {
            Timber.i("Unable to find a suitable refresh rate for ${fps}fps video")
            originalMode = null
        } else {
            Timber.i(
                "Video: ${fps.roundTo(
                    2,
                )}fps, Chosen refresh rate: ${bestMode.refreshRate.roundTo(2).toString() + "Hz"} (Mode: ${bestMode.modeId})",
            )
            changeRefreshRate(context, bestMode)
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

    @RequiresApi(Build.VERSION_CODES.M)
    private fun pickImpreciseMode(
        modes: List<Display.Mode>,
        fps: Float,
    ): Display.Mode? {
        // Round the input FPS *up* to the nearest integer.
        val targetFpsInt = ceil(fps.toDouble()).toInt()
        Timber.d("Target FPS (rounded up): $targetFpsInt Hz")

        // Avoid division by zero if fps rounds up to 0 (shouldn't happen with fps > 0 check earlier)
        if (targetFpsInt == 0) {
            Timber.w("Rounded target FPS is 0, cannot find multiple.")
            return null
        }

        // Find modes where the rounded refresh rate is a multiple of the rounded-up FPS.
        val suitableModes = modes.filter { mode ->
            val roundedRefreshRate = mode.refreshRate.roundToInt()
            // Check if refresh rate is a positive multiple of the target FPS
            roundedRefreshRate > 0 && roundedRefreshRate % targetFpsInt == 0
        }

        if (suitableModes.isEmpty()) {
            Timber.i("No suitable modes found where refresh rate is a multiple of $targetFpsInt Hz.")
            return null // No mode found that's a multiple
        }

        // Log the suitable modes found
        Timber.d("Suitable modes (multiple of $targetFpsInt Hz): ${suitableModes.joinToString { it.refreshRate.roundTo(2).toString() + "Hz" }}")

        // From the suitable modes, prefer the one with the highest actual refresh rate.
        // This implicitly prefers 60Hz over 30Hz, or 50Hz over 25Hz.
        val bestMode = suitableModes.maxByOrNull { it.refreshRate }

        Timber.d("Best imprecise mode chosen: ${bestMode?.refreshRate?.roundTo(2)}Hz (Mode ID: ${bestMode?.modeId})")

        return bestMode
    }

   @RequiresApi(Build.VERSION_CODES.M)
    private fun pickPreciseMode(
        modes: List<Display.Mode>,
        fps: Float,
    ): Display.Mode? {
        Timber.d("Attempting precise match for ${fps.roundTo(2)}fps")
        var newMode: Display.Mode? = null
        val targetFpsRounded = fps.roundTo(2)

        // Find a mode where the refresh rate exactly matches the rounded FPS
        for (mode in modes) {
            if (mode.refreshRate.roundTo(2) == targetFpsRounded) {
                newMode = mode
                Timber.d("Found precise match: ${newMode.refreshRate.roundTo(2)}Hz (Mode ID: ${newMode.modeId})")
                break // Found the exact match
            }
        }

        // Fallback: If no exact match and FPS is ~25, try finding 50Hz
        if (newMode == null && fps.roundToInt() == 25) {
            Timber.i("No precise match for ~25fps found. Looking for 50Hz mode as fallback.")
            // Find a mode with refresh rate exactly 50.0
            newMode = modes.find { it.refreshRate.roundTo(2) == 50.0f }
            if (newMode != null) {
                Timber.d("Found 50Hz fallback mode: ${newMode.refreshRate.roundTo(2)}Hz (Mode ID: ${newMode.modeId})")
            }
        }

        if (newMode == null) {
            Timber.i("No precise mode or suitable fallback found for ${fps.roundTo(2)}fps.")
        }

        return newMode
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
