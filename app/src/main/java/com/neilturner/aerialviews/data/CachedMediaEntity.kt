package com.neilturner.aerialviews.data

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(tableName = "cached_media", indices = [Index("playlistOrder")])
data class CachedMediaEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val playlistOrder: Int,
    val uri: String,
    val type: String,
    val source: String,
    val shortDescription: String,
    val pointsOfInterest: Map<Int, String>,
    val timeOfDay: String,
    val scene: String,
    val albumName: String,
    val title: String,
    val exifDate: String?,
    val exifOffset: String?,
    val exifLatitude: Double?,
    val exifLongitude: Double?,
    val exifCity: String?,
    val exifState: String?,
    val exifCountry: String?,
    val exifDescription: String?,
)
