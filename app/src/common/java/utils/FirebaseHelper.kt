@file:Suppress("PackageDirectoryMismatch")

package com.neilturner.aerialviews.utils

import androidx.core.os.bundleOf
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase

object FirebaseHelper {
    fun logException(ex: Throwable) {
        Firebase.crashlytics.recordException(ex)
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

    // log event with number (eg. number of photos or videos

    // log setting with true/false (eg. is webDAV or SMB used)
}
