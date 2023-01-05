package com.neilturner.aerialviews.utils

import android.content.Context
import android.content.pm.PackageManager

object PackageHelper {

    // https://stackoverflow.com/a/34194960/247257
    fun isFirstInstall(context: Context): Boolean {
        return try {
            val firstInstallTime: Long = context.packageManager
                .getPackageInfoCompat(context.packageName, 0).firstInstallTime
            val lastUpdateTime: Long = context.packageManager
                .getPackageInfoCompat(context.packageName, 0).lastUpdateTime
            firstInstallTime == lastUpdateTime
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
            true
        }
    }

    fun isPackageUpdate(context: Context): Boolean {
        return try {
            val firstInstallTime: Long = context.packageManager
                .getPackageInfoCompat(context.packageName, 0).firstInstallTime
            val lastUpdateTime: Long = context.packageManager
                .getPackageInfoCompat(context.packageName, 0).lastUpdateTime
            firstInstallTime != lastUpdateTime
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
            false
        }
    }
}
