package com.neilturner.aerialviews.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_music_tracks")
data class CachedMusicTrackEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val playlistOrder: Int,
    val uri: String,
    val source: String
)
