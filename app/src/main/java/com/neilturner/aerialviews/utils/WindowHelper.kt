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

// https://stackoverflow.com/a/64828067/247257

object WindowHelper {
    fun hideSystemUI(window: Window, view: View) {
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

        /* Copyright 2021 Google LLC.
        SPDX-License-Identifier: Apache-2.0 */

        // Determine whether the transition will be seamless.
        // Non-seamless transitions may cause a 1-2 second black screen.
        val refreshRates = display.mode?.alternativeRefreshRates?.toList()
        val willBeSeamless = refreshRates?.contains(newRefreshRate)
        if (willBeSeamless == true) {
            Log.i(TAG, "Trying seamless...")
            // Set the frame rate, but only if the transition will be seamless.
            surface.setFrameRate(
                newRefreshRate,
                Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE,
                Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS
            )
        } else {
            Log.i(TAG, "Trying non-seamless...")
            val prefersNonSeamless = (context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager)
                .matchContentFrameRateUserPreference == DisplayManager.MATCH_CONTENT_FRAMERATE_ALWAYS
            if (prefersNonSeamless) {
                // Show UX to inform the user that a switch is about to occur
                // showUxForNonSeamlessSwitchWithDelay();
                // Set the frame rate if the user has requested it to match content
                surface.setFrameRate(
                    newRefreshRate,
                    Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE,
                    Surface.CHANGE_FRAME_RATE_ALWAYS
                )
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    fun setLegacyRefreshRate(context: Context, newRefreshRate: Float) {
        // https://github.com/moneytoo/Player/blob/6d3dc72734d7d9d2df2267eaf35cc473ac1dd3b4/app/src/main/java/com/brouken/player/Utils.java

        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val display = displayManager.displays[0]
        val supportedModes = display.supportedModes
        val activeMode = display.mode

        Log.i(TAG, "Supported modes: ${supportedModes.size}")
        if (supportedModes.size > 1) {
            // Refresh rate >= video FPS
            val modesHigh = mutableListOf<Display.Mode>()

            // Max refresh rate
            var modeTop = activeMode
            var modesResolutionCount = 0

            // Filter only resolutions same as current
            for (mode in supportedModes) {
                if (mode.physicalWidth == activeMode.physicalWidth &&
                    mode.physicalHeight == activeMode.physicalHeight
                ) {
                    modesResolutionCount++

                    if (normRate(mode.refreshRate) >= normRate(newRefreshRate)) {
                        modesHigh.add(mode)
                    }

                    if (normRate(mode.refreshRate) > normRate(modeTop.refreshRate)) {
                        modeTop = mode
                    }
                }
            }

            Log.i(TAG, "Available modes: $modesResolutionCount")
            if (modesResolutionCount > 1) {
                var modeBest: Display.Mode? = null
                var modes = "Available refreshRates:"

                for (mode in modesHigh) {
                    modes += " " + mode.refreshRate
                    if (normRate(mode.refreshRate) % normRate(newRefreshRate) <= 0.0001f) {
                        if (modeBest == null || normRate(mode.refreshRate) > normRate(modeBest.refreshRate)) {
                            modeBest = mode
                        }
                    }
                }

                Log.i(TAG, "Trying to change window properties...")
                val activity = context as? Activity
                if (activity == null) {
                    Log.i(TAG, "Unable to get Window object")
                    return
                }

                val window = activity.window
                val layoutParams = window.attributes

                if (modeBest == null) {
                    modeBest = modeTop
                }

                val switchingModes = modeBest?.modeId != activeMode?.modeId
                if (switchingModes) {
                    Log.i(TAG, "Switching mode from ${activeMode?.modeId} to ${modeBest?.modeId}")
                    layoutParams.preferredDisplayModeId = modeBest?.modeId!!
                    window.attributes = layoutParams
                } else {
                    Log.i(TAG, "Already in mode ${activeMode?.modeId}, no need to change.")
                }

                if (BuildConfig.DEBUG) {
                    Toast.makeText(
                        activity,
                        modes + "\n" +
                            "Video frameRate: " + newRefreshRate + "\n" +
                            "Current display refreshRate: " + modeBest?.refreshRate,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        } else {
            Log.i(TAG, "Only 1 mode found, exiting")
        }
    }

    private fun normRate(rate: Float): Int {
        return (rate * 100f).toInt()
    }

    private const val TAG = "WindowHelper"
}
