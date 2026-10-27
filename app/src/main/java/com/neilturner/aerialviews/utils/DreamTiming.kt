package com.neilturner.aerialviews.utils

import android.os.SystemClock
import android.util.Log
import com.neilturner.aerialviews.BuildConfig

/**
 * Startup timestamps for the screensaver (dream) path, written to logcat as
 * `event;elapsedRealtimeMs[;detail]` so an adb harness can rebuild the timeline without a
 * debugger.
 *
 * [SystemClock.elapsedRealtime] is device-wide, so a benchmark running in a different process
 * can compare these values with its own timestamps. Only debug and beta flavours emit marks.
 */
object DreamTiming {
    private const val TAG = "AerialDreamTiming"

    private val enabled =
        BuildConfig.DEBUG ||
            BuildConfig.TEST_CONFIG ||
            BuildConfig.FLAVOR.contains("beta", ignoreCase = true)

    fun mark(
        event: String,
        detail: String = "",
    ) {
        if (!enabled) return
        val detailSuffix = if (detail.isEmpty()) "" else ";$detail"
        Log.i(TAG, "$event;${SystemClock.elapsedRealtime()}$detailSuffix")
    }
}
