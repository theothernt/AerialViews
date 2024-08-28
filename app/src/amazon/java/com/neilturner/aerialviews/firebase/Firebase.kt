package com.neilturner.aerialviews.firebase;

import androidx.annotation.NonNull
import com.google.firebase.analytics.analytics
import com.google.firebase.crashlytics.crashlytics
import com.google.firebase.Firebase as googleFirebase

class Firebase () {
    object crashlytics {
        fun recordException(@NonNull throwable: Throwable) {
            googleFirebase.crashlytics.recordException(throwable)
        }
    }
    companion object {
        val analytics = googleFirebase.analytics
    }
}
