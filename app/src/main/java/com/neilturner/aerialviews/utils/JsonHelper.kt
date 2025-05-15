package com.neilturner.aerialviews.utils

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Converter
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@OptIn(ExperimentalSerializationApi::class)
object JsonHelper {
    val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    fun buildSerializer(): Converter.Factory {
        val contentType = "application/json".toMediaType()
        return json.asConverterFactory(contentType)
    }

    internal suspend inline fun <reified T> parseJson(
        context: Context,
        resId: Int,
    ): T =
        withContext(Dispatchers.IO) {
            context.resources.openRawResource(resId).use {
                return@withContext json.decodeFromStream<T>(it)
            }
        }

    suspend fun parseJsonMap(
        context: Context,
        resId: Int,
    ): Map<String, String> =
        withContext(Dispatchers.IO) {
            context.resources.openRawResource(resId).use {
                return@withContext json.decodeFromStream<Map<String, String>>(it)
            }
        }
}
