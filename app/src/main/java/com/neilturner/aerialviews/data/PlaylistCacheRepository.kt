package com.neilturner.aerialviews.data

import android.content.Context
import androidx.core.net.toUri
import com.neilturner.aerialviews.models.MediaFetchResult
import com.neilturner.aerialviews.models.MediaPlaylist
import com.neilturner.aerialviews.models.TimeOfDayFilter
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
import kotlin.time.Duration.Companion.days

/**
 * Snapshot of the cache preferences.
 *
 * Injected rather than read directly so the repository can be exercised in JVM tests without
 * initialising Kotpref.
 */
data class PlaylistCachePrefs(
    val enabled: Boolean,
    val refresh: String,
    val resumeSame: Boolean,
) {
    companion object {
        fun current(): PlaylistCachePrefs =
            PlaylistCachePrefs(
                enabled = GeneralPrefs.playlistCache,
                refresh = GeneralPrefs.playlistCacheRefresh,
                resumeSame = GeneralPrefs.playlistCacheResumeBehaviour == "same",
            )
    }
}

/**
 * The cached table holds the full playlist in `playlistOrder` order. The time-of-day filter is
 * applied when the playlist is read, never when it is written, so the cache survives day/night
 * cycles without refetching providers.
 *
 * All saved positions and chunk offsets are in the *filtered* index space. SQL applies LIMIT/OFFSET
 * after the filter predicate, so the initial window and every lazy chunk describe the same list.
 */
class PlaylistCacheRepository(
    private val appContext: Context,
    private val dao: PlaylistCacheDao = AerialDatabase.getInstance(appContext).playlistCacheDao(),
    private val prefsProvider: () -> PlaylistCachePrefs = { PlaylistCachePrefs.current() },
) {
    suspend fun isCacheValid(
        settingsHash: String,
        filter: TimeOfDayFilter,
    ): Boolean =
        withContext(Dispatchers.IO) {
            val prefs = prefsProvider()
            if (!prefs.enabled) {
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

            val intervalWeeks = prefs.refresh.toIntOrNull() ?: -1

            if (intervalWeeks == -1) {
                // Valid until end of playlist. The filtered list can shrink when the time-of-day
                // period changes, so an out-of-range position means "restart", not "refetch".
                val filteredTotal = countFiltered(filter)
                val outOfRange = state.mediaPosition >= filteredTotal
                val isValid = filteredTotal > 0 && (outOfRange || state.mediaPosition < filteredTotal - 1)
                if (!isValid) {
                    Timber.i("PlaylistCache: Cache invalidated (reached end of playlist: ${state.mediaPosition}/$filteredTotal)")
                } else {
                    Timber.d("PlaylistCache: Cache valid (position ${state.mediaPosition}/$filteredTotal)")
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

    suspend fun getCachedPlaylist(filter: TimeOfDayFilter): MediaFetchResult? =
        withContext(Dispatchers.IO) {
            val state = dao.getPlaylistState() ?: return@withContext null
            if (state.totalMediaItems == 0) return@withContext null

            val resolvedFilter = resolveFilter(filter)
            val filteredTotal = dao.countMediaItems(resolvedFilter.allowedNames())
            if (filteredTotal == 0) {
                Timber.w("PlaylistCache: DB state exists but no media items match the current filter")
                return@withContext null
            }

            Timber.d(
                "PlaylistCache: Restoring state from DB. Position: ${state.mediaPosition}, " +
                    "cached: ${state.totalMediaItems}, filtered: $filteredTotal, period: ${resolvedFilter.period}",
            )

            // The saved media position is the last visual item that started. nextItem()
            // pre-increments, so restoring from this value starts the following item. If "same" is
            // chosen, decrement by 1 so pre-increment lands back on the saved position. A saved
            // position outside the current filtered list means the period changed: restart.
            val resumeSame = prefsProvider().resumeSame
            val resumePosition =
                when {
                    state.mediaPosition < 0 || state.mediaPosition >= filteredTotal -> -1
                    resumeSame -> (state.mediaPosition - 1).coerceIn(-1, filteredTotal - 1)
                    else -> state.mediaPosition.coerceIn(-1, filteredTotal - 1)
                }

            val windowLimit = 50
            val windowOffset = 0.coerceAtLeast(resumePosition - 5)
            Timber.d("PlaylistCache: Loading initial window. Offset: $windowOffset, Limit: $windowLimit")
            val cachedMediaChunks = dao.getMediaItemsChunkFiltered(resolvedFilter.allowedNames(), windowLimit, windowOffset)

            val cachedMusic = dao.getAllMusicTracksOrdered()

            if (cachedMediaChunks.isEmpty()) {
                Timber.w("PlaylistCache: DB state exists but no media items found")
                return@withContext null
            }

            val mediaList =
                try {
                    cachedMediaChunks.map { mapEntityToMedia(it) }
                } catch (e: Exception) {
                    Timber.e(e, "PlaylistCache: Failed to map cached media entities")
                    clearCache()
                    return@withContext null
                }

            val musicList =
                try {
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

            Timber.i(
                "PlaylistCache: Cache restored successfully. Music track: " +
                    "${state.musicTrackIndex}/${state.totalMusicTracks}",
            )

            Timber.d(
                "PlaylistCache: Last started position ${state.mediaPosition}, filtered total $filteredTotal, " +
                    "resumeSame=$resumeSame, startPosition set to $resumePosition",
            )

            MediaFetchResult(
                mediaPlaylist =
                    MediaPlaylist(
                        initialVideos = mediaList,
                        startPosition = resumePosition,
                        size = filteredTotal,
                        windowOffset = windowOffset,
                        fetchChunk = { offset, limit ->
                            Timber.d("PlaylistCache: Lazy fetching chunk: offset $offset, limit $limit")
                            getMediaChunk(offset, limit, resolvedFilter)
                        },
                    ),
                musicPlaylist = musicPlaylist,
                musicResumeIndex = state.musicTrackIndex,
                isFromCache = true,
            )
        }

    suspend fun getMediaChunk(
        offset: Int,
        limit: Int,
        filter: TimeOfDayFilter,
    ): List<AerialMedia> =
        withContext(Dispatchers.IO) {
            try {
                dao.getMediaItemsChunkFiltered(filter.allowedNames(), limit, offset).map { mapEntityToMedia(it) }
            } catch (e: Exception) {
                Timber.e(e, "PlaylistCache: Failed to map chunk")
                emptyList()
            }
        }

    /** Number of cached items that match [filter] right now, for the cache status screen. */
    suspend fun countMatching(filter: TimeOfDayFilter): Int =
        withContext(Dispatchers.IO) {
            countFiltered(filter)
        }

    private suspend fun countFiltered(filter: TimeOfDayFilter): Int = dao.countMediaItems(resolveFilter(filter).allowedNames())

    /**
     * If no videos match the preferred filter, fall back to the full list. This mirrors the
     * in-memory behaviour so a filter can never hide the entire library.
     */
    private suspend fun resolveFilter(filter: TimeOfDayFilter): TimeOfDayFilter {
        if (!filter.enabled) return TimeOfDayFilter.ALL
        val matchingVideos = dao.countVideoItems(filter.allowedNames())
        return if (matchingVideos == 0) {
            Timber.w("PlaylistCache: No videos match ${filter.period}; using unfiltered list")
            TimeOfDayFilter.ALL
        } else {
            filter
        }
    }

    private fun mapEntityToMedia(entity: CachedMediaEntity): AerialMedia =
        AerialMedia(
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
            audioUri = entity.audioUri?.toUri(),
        )

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
                    audioUri = m.audioUri?.toString(),
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
        if (prefsProvider().enabled) {
            dao.updateMediaPosition(position)
        }
    }

    suspend fun saveMusicTrackIndex(index: Int) {
        if (prefsProvider().enabled) {
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
