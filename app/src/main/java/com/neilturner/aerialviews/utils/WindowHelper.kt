@file:Suppress("unused")

package com.neilturner.aerialviews.utils

import android.app.Activity
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.Log
import android.view.Display
import android.view.Surface
import android.view.View
import android.view.Window
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.neilturner.aerialviews.BuildConfig
import kotlin.math.roundToInt

object WindowHelper {
    fun hideSystemUI(window: Window, view: View) {
        // https://stackoverflow.com/a/64828067/247257
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, view).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    fun showSystemUI(window: Window, view: View) {
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, view).show(WindowInsetsCompat.Type.systemBars())
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun setRefreshRate(context: Context, surface: Surface, display: Display, newRefreshRate: Float) {
        // https://gist.github.com/pflammertsma/5a453e24938722b4218528a3e5a60259#file-mainactivity-kt

        val refreshRates = display.mode?.alternativeRefreshRates?.toList()
        val willBeSeamless = refreshRates?.contains(newRefreshRate)
        if (willBeSeamless == true) {
            Log.i(TAG, "Trying seamless...")
            surface.setFrameRate(
                newRefreshRate,
                Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE,
                Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS
            )
        } else {
            Log.i(TAG, "Seamless not supported, trying legacy...")
            setLegacyRefreshRate(context, newRefreshRate)
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    fun setLegacyRefreshRate(context: Context, newRefreshRate: Float) {
        // https://github.com/moneytoo/Player/blob/6d3dc72734d7d9d2df2267eaf35cc473ac1dd3b4/app/src/main/java/com/brouken/player/Utils.java

        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val display = displayManager.displays.first()
        val supportedModes = display.supportedModes.sortedBy { it.refreshRate }
        val activeMode = display.mode

        // No additional modes supported, exit early
        if (supportedModes.size == 1) {
            Log.i(TAG, "Only 1 mode found, exiting")
            return
        }

        // Only use same resolution as current
        val suitableModes = getModesForResolution(supportedModes, activeMode, newRefreshRate)
        if (suitableModes.isEmpty()) {
            Log.i(TAG, "No suitable frame rates found at this resolution, exiting")
            return
        }

        var newMode: Display.Mode? = null
        for (mode in suitableModes) {
            if (mode.refreshRate.roundToInt() % newRefreshRate.roundToInt() == 0) {
                if (newMode == null || mode.refreshRate.roundToInt() > newMode.refreshRate.roundToInt()) {
                    newMode = mode
                }
            }
        }

        if (newMode == null) {
            Log.i(TAG, "No new mode chosen")
            return
        }

        val activity = context as? Activity
        if (activity == null) {
            Log.i(TAG, "Unable to get current Activity")
            return
        }

        val window = activity.window
        val switchingModes = newMode.modeId != activeMode?.modeId
        if (switchingModes) {
            Log.i(TAG, "Switching mode from ${activeMode?.modeId} to ${newMode.modeId}")
            window.attributes.preferredDisplayModeId = newMode.modeId
        } else {
            Log.i(TAG, "Already in mode ${activeMode?.modeId}, no need to change.")
        }

        val refreshRates = suitableModes.map {
            it.refreshRate.toString().take(5)
        }

        if (BuildConfig.DEBUG) {
            Toast.makeText(
                activity,
                "Available: ${refreshRates.joinToString(", ")}\n" +
                    "Selected: ${newMode.refreshRate.toString().take(5)} (${newRefreshRate.toString().take(5)} fps)",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    fun getModesForResolution(supportedModes: List<Display.Mode>, activeMode: Display.Mode, newRefreshRate: Float): List<Display.Mode> {
        val filteredModes = mutableListOf<Display.Mode>()

        for (mode in supportedModes) {
            if (mode.physicalWidth == activeMode.physicalWidth &&
                mode.physicalHeight == activeMode.physicalHeight
            ) {
                if (mode.refreshRate.roundToInt() >= newRefreshRate.roundToInt()) {
                    filteredModes.add(mode)
                }
            }
        }

        return filteredModes
    }

    private const val TAG = "WindowHelper"
}
