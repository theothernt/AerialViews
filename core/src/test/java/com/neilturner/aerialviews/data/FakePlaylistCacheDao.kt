package com.neilturner.aerialviews.data

/**
 * In-memory stand-in for [PlaylistCacheDao] that reproduces the SQL predicates used by the real
 * cache queries. It exists because Room needs an Android runtime, and the filtering rules are what
 * these tests are about, not Room itself.
 */
internal class FakePlaylistCacheDao : PlaylistCacheDao {
    val mediaItems = mutableListOf<CachedMediaEntity>()
    val musicTracks = mutableListOf<CachedMusicTrackEntity>()
    var state: PlaylistStateEntity? = null

    override suspend fun insertMediaItems(items: List<CachedMediaEntity>) {
        mediaItems += items
    }

    override suspend fun insertMusicTracks(tracks: List<CachedMusicTrackEntity>) {
        musicTracks += tracks
    }

    override suspend fun insertOrUpdateState(state: PlaylistStateEntity) {
        this.state = state
    }

    override suspend fun getAllMediaItemsOrdered(): List<CachedMediaEntity> = orderedMedia()

    override suspend fun countMediaItems(allowed: List<String>): Int = filteredMedia(allowed).size

    override suspend fun countVideoItems(allowed: List<String>): Int = mediaItems.count { it.type == VIDEO && it.timeOfDay in allowed }

    override suspend fun getMediaItemsChunkFiltered(
        allowed: List<String>,
        limit: Int,
        offset: Int,
    ): List<CachedMediaEntity> = filteredMedia(allowed).drop(offset).take(limit)

    override suspend fun getAllMusicTracksOrdered(): List<CachedMusicTrackEntity> = musicTracks.sortedBy { it.playlistOrder }

    override suspend fun getPlaylistState(): PlaylistStateEntity? = state

    override suspend fun updateMediaPosition(position: Int) {
        state = state?.copy(mediaPosition = position)
    }

    override suspend fun updateMusicTrackIndex(index: Int) {
        state = state?.copy(musicTrackIndex = index)
    }

    override suspend fun clearMediaItems() {
        mediaItems.clear()
    }

    override suspend fun clearMusicTracks() {
        musicTracks.clear()
    }

    override suspend fun clearState() {
        state = null
    }

    private fun orderedMedia(): List<CachedMediaEntity> = mediaItems.sortedBy { it.playlistOrder }

    private fun filteredMedia(allowed: List<String>): List<CachedMediaEntity> =
        orderedMedia().filter { it.type != VIDEO || it.timeOfDay in allowed }

    private companion object {
        const val VIDEO = "VIDEO"
    }
}
