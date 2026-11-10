@file:Suppress("unused")

package com.neilturner.aerialviews.utils

import android.view.View
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.preference.MultiSelectListPreference
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

// https://stackoverflow.com/a/53428355/247257
fun MultiSelectListPreference.setSummaryFromValues(values: Set<String>) {
    summary = values.joinToString(", ") { entries[findIndexOfValue(it)] }
}

// https://juliensalvi.medium.com/safe-delay-in-android-views-goodbye-handlers-hello-coroutines-cd47f53f0fbf
fun View.delayOnLifecycle(
    durationInMillis: Long,
    dispatcher: CoroutineDispatcher = Dispatchers.Main,
    block: () -> Unit,
): Job? =
    findViewTreeLifecycleOwner()?.let { lifecycleOwner ->
        lifecycleOwner.lifecycle.coroutineScope.launch(dispatcher) {
            delay(durationInMillis.milliseconds)
            block()
        }
    }
