package com.neilturner.aerialviews.utils

import android.os.Bundle
import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import com.google.firebase.crashlytics.crashlytics
import com.neilturner.aerialviews.core.telemetry.TelemetrySink
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Firebase implementation of [TelemetrySink].
 *
 * Lives in `:app` because only the Firebase flavors depend on Firebase. The logging-period gate
 * is preserved exactly as it was in `FirebaseHelper`; do not change its behaviour during the
 * migration.
 */
class FirebaseTelemetrySink : TelemetrySink {
    private fun isWithinLoggingPeriod(): Boolean {
        val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val endDate = simpleDateFormat.parse(LOGGING_END_DATE)
        val currentDate = Calendar.getInstance().time
        return currentDate.before(endDate)
    }

    override fun screenView(
        screenName: String,
        screenClass: Any,
    ) {
        val parameters =
            Bundle().apply {
                putString(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
                putString(FirebaseAnalytics.Param.SCREEN_CLASS, screenClass::class.java.simpleName)
            }
        Firebase.analytics.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, parameters)
    }

    override fun event(
        eventName: String,
        parameters: Bundle,
        alwaysLog: Boolean,
    ) {
        if (isWithinLoggingPeriod() || alwaysLog) {
            Firebase.analytics.logEvent(eventName, parameters)
        }
    }

    override fun recordException(
        throwable: Throwable?,
        alwaysLog: Boolean,
    ) {
        if (isWithinLoggingPeriod() || alwaysLog) {
            throwable?.let {
                Firebase.crashlytics.recordException(it)
            }
        }
    }

    override fun logMessage(
        message: String,
        alwaysLog: Boolean,
    ) {
        if (isWithinLoggingPeriod() || alwaysLog) {
            Firebase.crashlytics.log(message)
        }
    }

    override fun logKey(
        key: String,
        value: Any?,
        alwaysLog: Boolean,
    ) {
        if (isWithinLoggingPeriod() || alwaysLog) {
            when (value) {
                is String -> Firebase.crashlytics.setCustomKey(key, value)
                is Int -> Firebase.crashlytics.setCustomKey(key, value)
                is Long -> Firebase.crashlytics.setCustomKey(key, value)
                is Float -> Firebase.crashlytics.setCustomKey(key, value)
                is Double -> Firebase.crashlytics.setCustomKey(key, value)
                is Boolean -> Firebase.crashlytics.setCustomKey(key, value)
                else -> Firebase.crashlytics.setCustomKey(key, value.toString())
            }
        }
    }

    private companion object {
        const val LOGGING_END_DATE = "2026-11-01"
    }
}
