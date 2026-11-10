package com.neilturner.aerialviews.utils

import android.os.Bundle
import com.neilturner.aerialviews.core.telemetry.Telemetry

/**
 * Compatibility facade for the F-Droid flavor. Reporting is a no-op because
 * [TelemetryInitializer] installs [com.neilturner.aerialviews.core.telemetry.NoOpTelemetrySink].
 */
object FirebaseHelper {
    fun logException(ex: Throwable) = Telemetry.recordException(ex)

    fun analyticsScreenView(
        screenName: String,
        screenClass: Any,
    ) = Telemetry.screenView(screenName, screenClass)

    fun analyticsEvent(
        eventName: String,
        parameters: Bundle,
        alwaysLog: Boolean = false,
    ) = Telemetry.event(eventName, parameters, alwaysLog)

    fun crashlyticsException(
        ex: Throwable?,
        alwaysLog: Boolean = false,
    ) = Telemetry.recordException(ex, alwaysLog)

    fun crashlyticsLogMessage(
        error: String,
        alwaysLog: Boolean = false,
    ) = Telemetry.logMessage(error, alwaysLog)

    fun <T> crashlyticsLogKeys(
        key: String,
        value: T,
        alwaysLog: Boolean = false,
    ) = Telemetry.logKey(key, value, alwaysLog)
}
