package com.neilturner.aerialviews.data

import android.content.Context
import com.neilturner.aerialviews.models.MediaFetchResult
import com.neilturner.aerialviews.models.MediaPlaylist
import com.neilturner.aerialviews.models.enums.AerialMediaSource
import com.neilturner.aerialviews.models.enums.AerialMediaType
import com.neilturner.aerialviews.models.enums.SceneType
import com.neilturner.aerialviews.models.enums.TimeOfDay
import com.neilturner.aerialviews.models.music.MusicPlaylist
import com.neilturner.aerialviews.models.music.MusicTrack
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.models.videos.AerialExifMetadata
import com.neilturner.aerialviews.models.videos.AerialMedia
import com.neilturner.aerialviews.models.videos.AerialMediaMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import androidx.core.net.toUri
import kotlin.time.Duration.Companion.days

class PlaylistCacheRepository(
    private val appContext: Context,
) {
    private val dao = AerialDatabase.getInstance(appContext).playlistCacheDao()

    suspend fun isCacheValid(settingsHash: String): Boolean =
        withContext(Dispatchers.IO) {
            val cacheEnabled = GeneralPrefs.playlistCache
            if (!cacheEnabled) {
                Timber.d("PlaylistCache: Cache disabled in settings")
                clearCache()
                return@withContext false
            }

            val state =
                dao.getPlaylistState() ?: run {
                    Timber.d("PlaylistCache: No existing cache state found")
                    return@withContext false
                }

            // Check hash
            if (state.settingsHash != settingsHash) {
                Timber.i("PlaylistCache: Cache invalidated (settings changed). Hash: ${state.settingsHash} -> $settingsHash")
                return@withContext false
            }

            val refreshIntervalStr = GeneralPrefs.playlistCacheRefresh
            val intervalWeeks = refreshIntervalStr.toIntOrNull() ?: -1

            if (intervalWeeks == -1) {
                // Valid until end of playlist
                val isValid = state.mediaPosition < state.totalMediaItems - 1
                if (!isValid) {
                    Timber.i("PlaylistCache: Cache invalidated (reached end of playlist: ${state.mediaPosition}/${state.totalMediaItems})")
                } else {
                    Timber.d("PlaylistCache: Cache valid (position ${state.mediaPosition}/${state.totalMediaItems})")
                }
                return@withContext isValid
            } else {
                // Time based validity
                val cacheAgeMs = System.currentTimeMillis() - state.cachedAt
                val maxAgeMs = (intervalWeeks * 7).days.inWholeMilliseconds
                val isValid = cacheAgeMs < maxAgeMs
                if (!isValid) Timber.i("Cache invalidated: age limit reached ($intervalWeeks weeks)")
                return@withContext isValid
            }
        }

    suspend fun getCachedPlaylist(): MediaFetchResult? =
        withContext(Dispatchers.IO) {
            val state = dao.getPlaylistState() ?: return@withContext null
            if (state.totalMediaItems == 0) return@withContext null

            Timber.d("PlaylistCache: Restoring state from DB. Position: ${state.mediaPosition}, Total: ${state.totalMediaItems}")

            val windowLimit = 50
            val windowOffset = 0.coerceAtLeast(state.mediaPosition - 5)
            Timber.d("PlaylistCache: Loading initial window. Offset: $windowOffset, Limit: $windowLimit")
            val cachedMediaChunks = dao.getMediaItemsChunk(windowLimit, windowOffset)

            val cachedMusic = dao.getAllMusicTracksOrdered()

            if (cachedMediaChunks.isEmpty()) {
                Timber.w("PlaylistCache: DB state exists but no media items found")
                return@withContext null
            }

            val mediaList = try {
                cachedMediaChunks.map { mapEntityToMedia(it) }
            } catch (e: Exception) {
                Timber.e(e, "PlaylistCache: Failed to map cached media entities")
                clearCache()
                return@withContext null
            }

            val musicList = try {
                cachedMusic.map { entity ->
                    MusicTrack(
                        uri = entity.uri.toUri(),
                        source = enumValueOf<AerialMediaSource>(entity.source),
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "PlaylistCache: Failed to map cached music entities")
                clearCache()
                return@withContext null
            }

            val musicPlaylist =
                if (musicList.isNotEmpty()) {
                    MusicPlaylist(
                        tracks = musicList,
                        shuffle = state.musicShuffleEnabled,
                        repeat = state.musicRepeatEnabled,
                    )
                } else {
                    null
                }

            Timber.i("PlaylistCache: Cache restored successfully. Music track: ${state.musicTrackIndex}/${state.totalMusicTracks}")
            MediaFetchResult(
                mediaPlaylist =
                    MediaPlaylist(
                        initialVideos = mediaList,
                        startPosition = state.mediaPosition,
                        size = state.totalMediaItems,
                        windowOffset = windowOffset,
                        fetchChunk = { offset, limit ->
                            Timber.d("PlaylistCache: Lazy fetching chunk: offset $offset, limit $limit")
                            getMediaChunk(offset, limit)
                        },
                    ),
                musicPlaylist = musicPlaylist,
                musicResumeIndex = state.musicTrackIndex,
            )
        }

    suspend fun getMediaChunk(
        offset: Int,
        limit: Int,
    ): List<AerialMedia> =
        withContext(Dispatchers.IO) {
            try {
                val cachedMedia = dao.getMediaItemsChunk(limit, offset)
                cachedMedia.map { mapEntityToMedia(it) }
            } catch (e: Exception) {
                Timber.e(e, "PlaylistCache: Failed to map chunk")
                emptyList()
            }
        }

    private fun mapEntityToMedia(entity: CachedMediaEntity): AerialMedia {
        return AerialMedia(
            uri = entity.uri.toUri(),
            type = enumValueOf<AerialMediaType>(entity.type),
            source = enumValueOf<AerialMediaSource>(entity.source),
            metadata =
                AerialMediaMetadata(
                    shortDescription = entity.shortDescription,
                    pointsOfInterest = entity.pointsOfInterest,
                    timeOfDay = enumValueOf<TimeOfDay>(entity.timeOfDay),
                    scene = enumValueOf<SceneType>(entity.scene),
                    albumName = entity.albumName,
                    title = entity.title,
                    exif =
                        AerialExifMetadata(
                            date = entity.exifDate,
                            offset = entity.exifOffset,
                            latitude = entity.exifLatitude,
                            longitude = entity.exifLongitude,
                            city = entity.exifCity,
                            state = entity.exifState,
                            country = entity.exifCountry,
                            description = entity.exifDescription,
                        ),
                ),
        )
    }

    suspend fun cachePlaylist(
        media: List<AerialMedia>,
        musicPlaylist: MusicPlaylist?,
        settingsHash: String,
        shuffleEnabled: Boolean,
    ) = withContext(Dispatchers.IO) {
        val music = musicPlaylist?.tracks ?: emptyList()
        val mediaEntities =
            media.mapIndexed { index, m ->
                CachedMediaEntity(
                    playlistOrder = index,
                    uri = m.uri.toString(),
                    type = m.type.name,
                    source = m.source.name,
                    shortDescription = m.metadata.shortDescription,
                    pointsOfInterest = m.metadata.pointsOfInterest,
                    timeOfDay = m.metadata.timeOfDay.name,
                    scene = m.metadata.scene.name,
                    albumName = m.metadata.albumName,
                    title = m.metadata.title,
                    exifDate = m.metadata.exif.date,
                    exifOffset = m.metadata.exif.offset,
                    exifLatitude = m.metadata.exif.latitude,
                    exifLongitude = m.metadata.exif.longitude,
                    exifCity = m.metadata.exif.city,
                    exifState = m.metadata.exif.state,
                    exifCountry = m.metadata.exif.country,
                    exifDescription = m.metadata.exif.description,
                )
            }

        val musicEntities =
            music.mapIndexed { index, t ->
                CachedMusicTrackEntity(
                    playlistOrder = index,
                    uri = t.uri.toString(),
                    source = t.source.name,
                )
            }

        val state =
            PlaylistStateEntity(
                mediaPosition = -1,
                musicTrackIndex = 0,
                cachedAt = System.currentTimeMillis(),
                settingsHash = settingsHash,
                totalMediaItems = media.size,
                totalMusicTracks = music.size,
                shuffleEnabled = shuffleEnabled,
                musicShuffleEnabled = musicPlaylist?.shuffle ?: false,
                musicRepeatEnabled = musicPlaylist?.repeat ?: false,
            )

        dao.updateCache(mediaEntities, musicEntities, state)
        Timber.i("PlaylistCache: Saved new cache. Media: ${media.size}, Music: ${music.size}, Hash: $settingsHash")
    }

    suspend fun saveMediaPosition(position: Int) {
        if (GeneralPrefs.playlistCache) {
            dao.updateMediaPosition(position)
        }
    }

    suspend fun saveMusicTrackIndex(index: Int) {
        if (GeneralPrefs.playlistCache) {
            dao.updateMusicTrackIndex(index)
        }
    }

    suspend fun clearCache() =
        withContext(Dispatchers.IO) {
            try {
                dao.clearAll()
            } catch (e: Exception) {
                Timber.e(e, "PlaylistCache: Room clear failed, attempting file deletion")
                try {
                    AerialDatabase.closeAndReset()
                    appContext.deleteDatabase(AerialDatabase.dbName())
                } catch (e2: Exception) {
                    Timber.e(e2, "PlaylistCache: Failed to delete database file")
                }
            }
        }
}
