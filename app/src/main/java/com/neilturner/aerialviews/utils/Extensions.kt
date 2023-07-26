package com.neilturner.aerialviews.utils

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.preference.MultiSelectListPreference

// https://stackoverflow.com/a/36795003/247257
fun Any?.toStringOrEmpty() = this?.toString() ?: ""

val Uri.filename: String
    get() = this.lastPathSegment.toString()

// https://stackoverflow.com/a/74741495/247257
fun PackageManager.getPackageInfoCompat(packageName: String, flags: Int = 0): PackageInfo =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
    } else {
        @Suppress("DEPRECATION")
        getPackageInfo(packageName, flags)
    }

// https://stackoverflow.com/a/53428355/247257
fun MultiSelectListPreference.setSummaryFromValues(values: Set<String>) {
    summary = values.joinToString(", ") { entries[findIndexOfValue(it)] }
}
