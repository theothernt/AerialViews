@file:Suppress("unused")

package com.neilturner.aerialviews.utils

import android.animation.ValueAnimator
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.view.View
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController
import timber.log.Timber

object WindowHelper {
    fun hideSystemUI(
        window: Window,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            @Suppress("DEPRECATION")
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_FULLSCREEN
                    )
        }
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
