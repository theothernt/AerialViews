package com.neilturner.aerialviews.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlist_state")
data class PlaylistStateEntity(
    @PrimaryKey
    val id: Int = 1, // Fixed single row
    val mediaPosition: Int,
    val musicTrackIndex: Int,
    val cachedAt: Long,
    val settingsHash: String,
    val totalMediaItems: Int,
    val totalMusicTracks: Int,
    val shuffleEnabled: Boolean,
    val musicShuffleEnabled: Boolean,
    val musicRepeatEnabled: Boolean
)
