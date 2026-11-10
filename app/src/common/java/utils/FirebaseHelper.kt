package com.neilturner.aerialviews.utils

import android.os.Bundle
import com.neilturner.aerialviews.core.telemetry.Telemetry

/**
 * Compatibility facade for `:app` call sites that have not moved to [Telemetry] yet.
 *
 * It adds no behaviour of its own; the Firebase implementation lives in [FirebaseTelemetrySink].
 * Delete this file once the last `:app` caller is migrated.
 */
object FirebaseHelper {
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
