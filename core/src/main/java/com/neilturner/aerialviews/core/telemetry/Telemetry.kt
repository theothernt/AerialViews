package com.neilturner.aerialviews.core.telemetry

import android.os.Bundle

/**
 * Crash, analytics and diagnostic reporting used by library code.
 *
 * Implementations live in `:app`, which owns the Firebase dependency. `:core` and `:playback`
 * depend only on this interface, so F-Droid stays Firebase-free without a source-set swap.
 */
interface TelemetrySink {
    fun screenView(
        screenName: String,
        screenClass: Any,
    )

    fun event(
        eventName: String,
        parameters: Bundle,
        alwaysLog: Boolean = false,
    )

    fun recordException(
        throwable: Throwable?,
        alwaysLog: Boolean = false,
    )

    fun logMessage(
        message: String,
        alwaysLog: Boolean = false,
    )

    fun logKey(
        key: String,
        value: Any?,
        alwaysLog: Boolean = false,
    )
}

/** Used until `:app` initializes the facade, and on builds with no reporting backend. */
object NoOpTelemetrySink : TelemetrySink {
    override fun screenView(
        screenName: String,
        screenClass: Any,
    ) = Unit

    override fun event(
        eventName: String,
        parameters: Bundle,
        alwaysLog: Boolean,
    ) = Unit

    override fun recordException(
        throwable: Throwable?,
        alwaysLog: Boolean,
    ) = Unit

    override fun logMessage(
        message: String,
        alwaysLog: Boolean,
    ) = Unit

    override fun logKey(
        key: String,
        value: Any?,
        alwaysLog: Boolean,
    ) = Unit
}

/**
 * Process-wide entry point, initialized once by `:app` before any library code reports.
 *
 * Library code calls these methods directly; the implementation is chosen per flavor in `:app`.
 */
object Telemetry : TelemetrySink {
    @Volatile
    private var sink: TelemetrySink = NoOpTelemetrySink

    fun initialize(sink: TelemetrySink) {
        this.sink = sink
    }

    override fun screenView(
        screenName: String,
        screenClass: Any,
    ) = sink.screenView(screenName, screenClass)

    override fun event(
        eventName: String,
        parameters: Bundle,
        alwaysLog: Boolean,
    ) = sink.event(eventName, parameters, alwaysLog)

    override fun recordException(
        throwable: Throwable?,
        alwaysLog: Boolean,
    ) = sink.recordException(throwable, alwaysLog)

    override fun logMessage(
        message: String,
        alwaysLog: Boolean,
    ) = sink.logMessage(message, alwaysLog)

    override fun logKey(
        key: String,
        value: Any?,
        alwaysLog: Boolean,
    ) = sink.logKey(key, value, alwaysLog)
}
