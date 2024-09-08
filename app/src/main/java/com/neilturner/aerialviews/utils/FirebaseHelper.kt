package com.neilturner.aerialviews.utils

import androidx.core.os.bundleOf
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.crashlytics.ktx.crashlytics
import com.neilturner.aerialviews.BuildConfig

object FirebaseHelper {
    fun logException(ex: Throwable) {
        if (BuildConfig.FIREBASE_AVAILABLE) {
            com.google.firebase.ktx.Firebase.crashlytics.recordException(ex)
        }
    }

    fun logScreenView(
        screenName: String,
        screenClass: Any,
    ) {
        if (BuildConfig.FIREBASE_AVAILABLE) {
            logFirebaseScreenView(screenName, screenClass::javaClass.name)
        }
    }

    private fun logFirebaseScreenView(
        screenName: String,
        activityName: String,
    ) {
        val parameters =
            bundleOf(
                Pair(com.google.firebase.analytics.FirebaseAnalytics.Param.SCREEN_NAME, screenName),
                Pair(com.google.firebase.analytics.FirebaseAnalytics.Param.SCREEN_CLASS, activityName),
            )

        com.google.firebase.ktx.Firebase.analytics.logEvent(com.google.firebase.analytics.FirebaseAnalytics.Event.SCREEN_VIEW, parameters)
    }

    // log event with number (eg. number of photos or videos

    // log setting with true/false (eg. is webDAV or SMB used)
}
