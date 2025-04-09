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

    fun logExceptionIfRecent(ex: Throwable) {
        val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val endDate = simpleDateFormat.parse("2025-06-30")
        val currentDate = Calendar.getInstance().time

        if (currentDate.before(endDate)) {
            logException(ex)
        }
    }
}
