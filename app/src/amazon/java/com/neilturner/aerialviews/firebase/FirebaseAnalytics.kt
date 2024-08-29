package com.neilturner.aerialviews.firebase

import com.google.firebase.analytics.FirebaseAnalytics as google_FirebaseAnalytics

class FirebaseAnalytics {
    class Param :google_FirebaseAnalytics.Param() {
        companion object {
            val SCREEN_NAME = google_FirebaseAnalytics.Param.SCREEN_NAME
            val SCREEN_CLASS = google_FirebaseAnalytics.Param.SCREEN_CLASS
        }
    }

    class Event :google_FirebaseAnalytics.Event() {
        companion object {
            val SCREEN_VIEW = google_FirebaseAnalytics.Event.SCREEN_VIEW
        }
    }
}