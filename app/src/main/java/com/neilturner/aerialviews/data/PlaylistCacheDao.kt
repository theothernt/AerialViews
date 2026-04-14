package com.neilturner.aerialviews.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface PlaylistCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertMediaItems(items: List<CachedMediaEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertMusicTracks(tracks: List<CachedMusicTrackEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrUpdateState(state: PlaylistStateEntity)

    @Query("SELECT * FROM cached_media ORDER BY playlistOrder ASC")
    fun getAllMediaItemsOrdered(): List<CachedMediaEntity>

    @Query("SELECT * FROM cached_music_tracks ORDER BY playlistOrder ASC")
    fun getAllMusicTracksOrdered(): List<CachedMusicTrackEntity>

    @Query("SELECT * FROM playlist_state WHERE id = 1")
    fun getPlaylistState(): PlaylistStateEntity?

    @Query("UPDATE playlist_state SET mediaPosition = :position WHERE id = 1")
    fun updateMediaPosition(position: Int)

    @Query("UPDATE playlist_state SET musicTrackIndex = :index WHERE id = 1")
    fun updateMusicTrackIndex(index: Int)

    @Query("DELETE FROM cached_media")
    fun clearMediaItems()

    @Query("DELETE FROM cached_music_tracks")
    fun clearMusicTracks()

    @Query("DELETE FROM playlist_state")
    fun clearState()

    @Transaction
    fun clearAll() {
        clearMediaItems()
        clearMusicTracks()
        clearState()
    }
}
