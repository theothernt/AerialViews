package com.neilturner.aerialviews.utils

import androidx.core.os.bundleOf
import com.neilturner.aerialviews.BuildConfig
import com.neilturner.aerialviews.firebase.Firebase
import com.neilturner.aerialviews.firebase.FirebaseAnalytics

object LoggingHelper {
    private val firebaseAnalytics = Firebase.analytics

    fun logScreenView(
        screenName: String,
        activityName: String,
    ) {
        if (BuildConfig.FIREBASE_AVAILABLE) {
            val parameters =
                bundleOf(
                    Pair(FirebaseAnalytics.Param.SCREEN_NAME, screenName),
                    Pair(FirebaseAnalytics.Param.SCREEN_CLASS, activityName),
                )

            firebaseAnalytics.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, parameters)
        }
    }
}
