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
    private const val LOGGING_END_DATE = "2025-08-31"

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

    fun logCustomKeysIfRecent(
        key: String,
        value: String,
    ) {
        if (isWithinLoggingPeriod()) {
            Firebase.crashlytics.setCustomKey(key, value)
        }
    }
}
