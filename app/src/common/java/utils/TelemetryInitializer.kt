package com.neilturner.aerialviews.utils

import com.neilturner.aerialviews.core.telemetry.TelemetrySink

/**
 * Per-flavor selection of the telemetry backend. `:app`'s `Application` passes the result to
 * [com.neilturner.aerialviews.core.telemetry.Telemetry.initialize].
 */
object TelemetryInitializer {
    fun create(): TelemetrySink = FirebaseTelemetrySink()
}
