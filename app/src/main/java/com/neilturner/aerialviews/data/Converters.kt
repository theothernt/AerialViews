package com.neilturner.aerialviews.data

import androidx.room.TypeConverter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromPointsMap(map: Map<Int, String>): String {
        return try {
            json.encodeToString(map)
        } catch (e: Exception) {
            "{}"
        }
    }

    @TypeConverter
    fun toPointsMap(data: String): Map<Int, String> {
        return try {
            json.decodeFromString<Map<Int, String>>(data)
        } catch (e: Exception) {
            emptyMap()
        }
    }
}
