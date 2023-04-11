package com.neilturner.aerialviews.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class PackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Is called when the package is replaced
        // Is NOT called on first install!
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val migration = MigrationHelper(context)
            migration.upgradeSettings()
        }
    }
}
