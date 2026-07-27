package com.neilturner.aerialviews.models.prefs

import android.content.Context
import com.chibatching.kotpref.KotprefModel

fun KotprefModel.settingsHashWithPrefix(vararg prefixes: String): String {
    val sharedPreferences = context.getSharedPreferences(kotprefName, Context.MODE_PRIVATE)
    val all = sharedPreferences.all
    val parts =
        all.entries
            .filter { entry -> prefixes.any { entry.key.startsWith(it) } }
            .sortedBy { it.key }
            .map { "${it.key}=${it.value}" }
    return parts.joinToString("|").hashCode().toString()
}
