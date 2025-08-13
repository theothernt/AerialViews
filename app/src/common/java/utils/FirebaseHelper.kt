@file:Suppress("PackageDirectoryMismatch")

package com.neilturner.aerialviews.utils

import androidx.core.os.bundleOf
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object FirebaseHelper {
    private const val LOGGING_END_DATE = "2025-10-01"

    private fun isWithinLoggingPeriod(): Boolean {
        val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val endDate = simpleDateFormat.parse(LOGGING_END_DATE)
        val currentDate = Calendar.getInstance().time
        return currentDate.before(endDate)
    }

    fun logScreenView(
        screenName: String,
        screenClass: Any,
    ) {
        val parameters =
            bundleOf(
                Pair(FirebaseAnalytics.Param.SCREEN_NAME, screenName),
                Pair(FirebaseAnalytics.Param.SCREEN_CLASS, screenClass::class.java.simpleName),
            )

        Firebase.analytics.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, parameters)
    }

    fun logException(ex: Throwable) {
        Firebase.crashlytics.recordException(ex)
    }

    fun logExceptionIfRecent(ex: Throwable?) {
        if (isWithinLoggingPeriod()) {
            ex?.let { logException(it) }
        }
    }

    fun logIfRecent(error: String) {
        if (isWithinLoggingPeriod()) {
            Firebase.crashlytics.log(error)
        }
    }

    fun <T> logCustomKeysIfRecent(
        key: String,
        value: T,
    ) {
        if (isWithinLoggingPeriod()) {
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
}
