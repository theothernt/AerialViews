@file:Suppress("unused")

package com.neilturner.aerialviews.utils

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.view.View
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.preference.MultiSelectListPreference
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

// https://stackoverflow.com/a/36795003/247257
fun Any?.toStringOrEmpty() = this?.toString() ?: ""

val Uri.filename: String
    get() = this.lastPathSegment.toString()

val Uri.filenameWithoutExtension: String
    get() {
        val filename = this.lastPathSegment.toStringOrEmpty()
        val index = filename.lastIndexOf(".")
        return if (index > 0) {
            filename.substring(0, index)
        } else {
            filename
        }
    }

// https://stackoverflow.com/a/74741495/247257
fun PackageManager.getPackageInfoCompat(
    packageName: String,
    flags: Int = 0,
): PackageInfo =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
    } else {
        getPackageInfo(packageName, flags)
    }

// https://stackoverflow.com/a/53428355/247257
fun MultiSelectListPreference.setSummaryFromValues(values: Set<String>) {
    summary = values.joinToString(", ") { entries[findIndexOfValue(it)] }
}

// https://stackoverflow.com/a/64913206/247257
fun Any?.toBoolean() = this?.toString().equals("true", ignoreCase = true)

// https://stackoverflow.com/a/41855007/247257
inline fun <reified T : Enum<T>> enumContains(name: String): Boolean = enumValues<T>().any { it.name == name }

// https://stackoverflow.com/a/67843987/247257
fun String.capitalise(): String =
    this.replaceFirstChar {
        if (it.isLowerCase()) {
            it.titlecase(Locale.getDefault())
        } else {
            it.toString()
        }
    }

// https://juliensalvi.medium.com/safe-delay-in-android-views-goodbye-handlers-hello-coroutines-cd47f53f0fbf
fun View.delayOnLifecycle(
    durationInMillis: Long,
    dispatcher: CoroutineDispatcher = Dispatchers.Main,
    block: () -> Unit,
): Job? =
    findViewTreeLifecycleOwner()?.let { lifecycleOwner ->
        lifecycleOwner.lifecycle.coroutineScope.launch(dispatcher) {
            delay(durationInMillis)
            block()
        }
    }

// https://stackoverflow.com/a/59513133/247257
fun Float.roundTo(n: Int): Float = "%.${n}f".format(Locale.ENGLISH, this).toFloat()

fun Double.roundTo(n: Int): Double = "%.${n}f".format(Locale.ENGLISH, this).toDouble()

fun <T> List<T>.parallelForEachCompat(action: (T) -> Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        this.parallelStream().forEach(action)
    } else {
        this.forEach(action)
    }
}