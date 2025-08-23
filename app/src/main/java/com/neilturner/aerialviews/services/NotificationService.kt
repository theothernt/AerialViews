package com.neilturner.aerialviews.services

import android.service.notification.NotificationListenerService
import timber.log.Timber

class NotificationService : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        Timber.i("NotificationService connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Timber.i("NotificationService disconnected")
    }

    override fun onCreate() {
        super.onCreate()
        Timber.i("NotificationService created")
    }

    override fun onDestroy() {
        Timber.i("NotificationService destroyed")
        super.onDestroy()
    }
}
