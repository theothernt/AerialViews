@file:Suppress("unused")

package com.neilturner.aerialviews.utils

import android.animation.ValueAnimator
import android.content.Context
import android.provider.Settings
import android.view.View
import android.view.Window
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import timber.log.Timber

object WindowHelper {
    fun hideSystemUI(
        window: Window,
        view: View,
    ) {
        // https://stackoverflow.com/a/64828067/247257
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, view).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    fun showSystemUI(
        window: Window,
        view: View,
    ) {
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, view).show(WindowInsetsCompat.Type.systemBars())
    }

    // https://stackoverflow.com/a/41238583/247257
    fun resetSystemAnimationDuration(context: Context) {
        // Get duration scale from the global settings.
        var durationScale =
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                0f,
            )

        // If global duration scale is not 1 (default), try to override it
        // for the current application.
        if (durationScale != 1f) {
            try {
                ValueAnimator::class.java.getMethod("setDurationScale", Float::class.java).invoke(null, 1f)
                durationScale = 1f
            } catch (t: Throwable) {
                // It means something bad happened, and animations are still
                // altered by the global settings. You should warn the user and
                // exit application.
            }
        }
        Timber.i("Duration scale: $durationScale")
    }
}
