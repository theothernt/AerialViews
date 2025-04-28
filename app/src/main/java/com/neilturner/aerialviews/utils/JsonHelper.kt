package com.neilturner.aerialviews.utils

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

object JsonHelper {
    val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    internal suspend inline fun <reified T> parseJson(
        context: Context,
        resId: Int,
    ): T =
        withContext(Dispatchers.IO) {
            val jsonString = readJsonFromRawResource(context, resId)
            return@withContext json.decodeFromString<T>(jsonString)
        }

    suspend fun parseJsonMap(
        context: Context,
        resId: Int,
    ): Map<String, String> =
        withContext(Dispatchers.IO) {
            val jsonString = readJsonFromRawResource(context, resId)
            return@withContext json.decodeFromString<Map<String, String>>(jsonString)
        }

    private fun readJsonFromRawResource(
        context: Context,
        resourceId: Int,
    ): String =
        context.resources.openRawResource(resourceId).bufferedReader().use {
            it.readText()
        }
}
