package com.neilturner.aerialviews.data

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert

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

    /**
     * Counts the rows that survive the time-of-day filter. Photos, and any non-video type, always
     * match; only videos are restricted to [allowed].
     */
    @Query("SELECT COUNT(*) FROM cached_media WHERE (type != 'VIDEO' OR timeOfDay IN (:allowed))")
    suspend fun countMediaItems(allowed: List<String>): Int

    /** Counts matching videos, used to decide whether the filter should fall back to everything. */
    @Query("SELECT COUNT(*) FROM cached_media WHERE type = 'VIDEO' AND timeOfDay IN (:allowed)")
    suspend fun countVideoItems(allowed: List<String>): Int

    /**
     * Returns a window of the filtered playlist. LIMIT/OFFSET are applied after the filter, so the
     * offsets are in the same index space as the filtered total and the saved position.
     */
    @Query(
        "SELECT * FROM cached_media WHERE (type != 'VIDEO' OR timeOfDay IN (:allowed)) " +
            "ORDER BY playlistOrder ASC LIMIT :limit OFFSET :offset",
    )
    suspend fun getMediaItemsChunkFiltered(
        allowed: List<String>,
        limit: Int,
        offset: Int,
    ): List<CachedMediaEntity>

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
        state: PlaylistStateEntity,
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
