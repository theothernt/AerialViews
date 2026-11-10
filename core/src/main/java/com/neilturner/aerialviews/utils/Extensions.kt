@file:Suppress("unused")

package com.neilturner.aerialviews.utils

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
            filename.take(index)
        } else {
            filename
        }
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

// https://stackoverflow.com/a/78022759/247257
fun String.capitaliseEachWord(): String {
    val regex = "(\\b[a-z](?!\\s))".toRegex()
    return this.replace(regex) { it.value.uppercase() }
}

// https://stackoverflow.com/a/59513133/247257
fun Float.roundTo(n: Int): Float = "%.${n}f".format(Locale.ENGLISH, this).toFloat()

fun Double.roundTo(n: Int): Double = "%.${n}f".format(Locale.ENGLISH, this).toDouble()

suspend fun <T> List<T>.parallelForEach(action: suspend (T) -> Unit) =
    coroutineScope {
        map { item ->
            async(Dispatchers.Default) {
                action(item)
            }
        }.awaitAll()
    }
