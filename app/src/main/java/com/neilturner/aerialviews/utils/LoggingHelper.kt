package com.neilturner.aerialviews.utils

import androidx.core.os.bundleOf
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase

object LoggingHelper {

    private val firebaseAnalytics = Firebase.analytics
    fun logScreenView(screenName: String, activityName: String) {
        val parameters = bundleOf(
            Pair(FirebaseAnalytics.Param.SCREEN_NAME, screenName),
            Pair(FirebaseAnalytics.Param.SCREEN_CLASS, activityName)
        )

        firebaseAnalytics.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, parameters)
    }
}
