package com.neilturner.aerialviews.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

@Dao
interface PlaylistCacheDao {
    @Upsert
    suspend fun insertMediaItems(items: List<CachedMediaEntity>)

    @Upsert
    suspend fun insertMusicTracks(tracks: List<CachedMusicTrackEntity>)

    @Upsert
    suspend fun insertOrUpdateState(state: PlaylistStateEntity)

    @Query("SELECT * FROM cached_media ORDER BY playlistOrder ASC")
    suspend fun getAllMediaItemsOrdered(): List<CachedMediaEntity>

    @Query("SELECT * FROM cached_media ORDER BY playlistOrder ASC LIMIT :limit OFFSET :offset")
    suspend fun getMediaItemsChunk(limit: Int, offset: Int): List<CachedMediaEntity>

    @Query("SELECT * FROM cached_music_tracks ORDER BY playlistOrder ASC")
    suspend fun getAllMusicTracksOrdered(): List<CachedMusicTrackEntity>

    @Query("SELECT * FROM playlist_state WHERE id = 1")
    suspend fun getPlaylistState(): PlaylistStateEntity?

    @Query("UPDATE playlist_state SET mediaPosition = :position WHERE id = 1")
    suspend fun updateMediaPosition(position: Int)

    @Query("UPDATE playlist_state SET musicTrackIndex = :index WHERE id = 1")
    suspend fun updateMusicTrackIndex(index: Int)

    @Query("DELETE FROM cached_media")
    suspend fun clearMediaItems()

    @Query("DELETE FROM cached_music_tracks")
    suspend fun clearMusicTracks()

    @Query("DELETE FROM playlist_state")
    suspend fun clearState()

    @Transaction
    suspend fun updateCache(
        media: List<CachedMediaEntity>,
        music: List<CachedMusicTrackEntity>,
        state: PlaylistStateEntity
    ) {
        clearMediaItems()
        clearMusicTracks()
        clearState()
        insertMediaItems(media)
        insertMusicTracks(music)
        insertOrUpdateState(state)
    }

    @Transaction
    suspend fun clearAll() {
        clearMediaItems()
        clearMusicTracks()
        clearState()
    }
}
