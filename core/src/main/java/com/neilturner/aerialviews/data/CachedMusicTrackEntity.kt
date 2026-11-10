package com.neilturner.aerialviews.data

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(tableName = "cached_music_tracks", indices = [Index("playlistOrder")])
data class CachedMusicTrackEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val playlistOrder: Int,
    val uri: String,
    val source: String,
)
