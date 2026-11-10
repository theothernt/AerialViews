package com.neilturner.aerialviews.utils

import com.neilturner.aerialviews.core.telemetry.NoOpTelemetrySink
import com.neilturner.aerialviews.core.telemetry.TelemetrySink

/**
 * F-Droid has no Firebase dependency, so reporting is a no-op. This replaces the old
 * `src/fdroid/java` copy of `FirebaseHelper`.
 */
object TelemetryInitializer {
    fun create(): TelemetrySink = NoOpTelemetrySink
}
