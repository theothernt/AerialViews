@file:Suppress("unused")

package com.neilturner.aerialviews.ui.helpers

import android.content.Context
import android.content.pm.PackageManager
import com.neilturner.aerialviews.utils.getPackageInfoCompat

object PackageHelper {
    // https://stackoverflow.com/a/34194960/247257
    fun isFirstInstall(context: Context): Boolean =
        try {
            val firstInstallTime: Long =
                context.packageManager
                    .getPackageInfoCompat(context.packageName, 0)
                    .firstInstallTime
            val lastUpdateTime: Long =
                context.packageManager
                    .getPackageInfoCompat(context.packageName, 0)
                    .lastUpdateTime
            firstInstallTime == lastUpdateTime
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
            true
        }

    fun isPackageUpdate(context: Context): Boolean =
        try {
            val firstInstallTime: Long =
                context.packageManager
                    .getPackageInfoCompat(context.packageName, 0)
                    .firstInstallTime
            val lastUpdateTime: Long =
                context.packageManager
                    .getPackageInfoCompat(context.packageName, 0)
                    .lastUpdateTime
            firstInstallTime != lastUpdateTime
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
            false
        }
}
