package com.neilturner.aerialviews.utils

import android.os.Bundle
import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import com.google.firebase.crashlytics.crashlytics
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object FirebaseHelper {
    private const val LOGGING_END_DATE = "2026-05-01"

    private fun isWithinLoggingPeriod(): Boolean {
        val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val endDate = simpleDateFormat.parse(LOGGING_END_DATE)
        val currentDate = Calendar.getInstance().time
        return currentDate.before(endDate)
    }

    fun analyticsScreenView(
        screenName: String,
        screenClass: Any,
    ) {
        try {
            val parameters =
                Bundle().apply {
                    putString(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
                    putString(FirebaseAnalytics.Param.SCREEN_CLASS, screenClass::class.java.simpleName)
                }
            Firebase.analytics.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, parameters)
        } catch (e: Exception) {
            // Firebase analytics not initialized - ignore
        }
    }

    fun analyticsEvent(
        eventName: String,
        parameters: Bundle,
        alwaysLog: Boolean = false,
    ) {
        if (isWithinLoggingPeriod() || alwaysLog) {
            try {
                Firebase.analytics.logEvent(eventName, parameters)
            } catch (e: Exception) {
                // Firebase analytics not initialized - ignore
            }
        }
    }

    fun crashlyticsException(
        ex: Throwable?,
        alwaysLog: Boolean = false,
    ) {
        if (isWithinLoggingPeriod() || alwaysLog) {
            ex?.let {
                try {
                    Firebase.crashlytics.recordException(ex)
                } catch (e: NullPointerException) {
                    // FirebaseCrashlytics not initialized - ignore
                }
            }
        }
    }

    fun crashlyticsLogMessage(
        error: String,
        alwaysLog: Boolean = false,
    ) {
        if (isWithinLoggingPeriod() || alwaysLog) {
            try {
                Firebase.crashlytics.log(error)
            } catch (e: NullPointerException) {
                // FirebaseCrashlytics not initialized - ignore
            }
        }
    }

    fun <T> crashlyticsLogKeys(
        key: String,
        value: T,
        alwaysLog: Boolean = false,
    ) {
        if (isWithinLoggingPeriod() || alwaysLog) {
            try {
                when (value) {
                    is String -> Firebase.crashlytics.setCustomKey(key, value)
                    is Int -> Firebase.crashlytics.setCustomKey(key, value)
                    is Long -> Firebase.crashlytics.setCustomKey(key, value)
                    is Float -> Firebase.crashlytics.setCustomKey(key, value)
                    is Double -> Firebase.crashlytics.setCustomKey(key, value)
                    is Boolean -> Firebase.crashlytics.setCustomKey(key, value)
                    else -> Firebase.crashlytics.setCustomKey(key, value.toString())
                }
            } catch (e: NullPointerException) {
                // FirebaseCrashlytics not initialized - ignore
            }
        }
    }
}
