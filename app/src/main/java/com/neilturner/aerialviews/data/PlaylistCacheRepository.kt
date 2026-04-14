package com.neilturner.aerialviews.data

import android.content.Context
import android.net.Uri
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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber

class PlaylistCacheRepository(context: Context) {
    private val dao = AerialDatabase.getInstance(context).playlistCacheDao()

    suspend fun isCacheValid(settingsHash: String): Boolean = withContext(Dispatchers.IO) {
        val cacheEnabled = GeneralPrefs.enablePlaylistCache
        if (!cacheEnabled) {
            clearCache()
            return@withContext false
        }
        
        val state = dao.getPlaylistState() ?: return@withContext false

        // Check hash
        if (state.settingsHash != settingsHash) {
            Timber.i("Cache invalidated: settings hash changed")
            return@withContext false
        }

        val refreshIntervalStr = GeneralPrefs.playlistCacheRefreshInterval
        val intervalWeeks = refreshIntervalStr.toIntOrNull() ?: -1

        if (intervalWeeks == -1) {
            // Valid until end of playlist
            val isValid = state.mediaPosition < state.totalMediaItems
            if (!isValid) Timber.i("Cache invalidated: reached end of playlist")
            return@withContext isValid
        } else {
            // Time based validity
            val cacheAgeMs = System.currentTimeMillis() - state.cachedAt
            val maxAgeMs = intervalWeeks * 7L * 24L * 60L * 60L * 1000L
            val isValid = cacheAgeMs < maxAgeMs
            if (!isValid) Timber.i("Cache invalidated: age limit reached ($intervalWeeks weeks)")
            return@withContext isValid
        }
    }

    suspend fun getCachedPlaylist(): MediaFetchResult? = withContext(Dispatchers.IO) {
        val state = dao.getPlaylistState() ?: return@withContext null
        val cachedMedia = dao.getAllMediaItemsOrdered()
        val cachedMusic = dao.getAllMusicTracksOrdered()

        if (cachedMedia.isEmpty()) return@withContext null

        val mediaList = cachedMedia.map { entity ->
            val pointsMap = try {
                Json.decodeFromString<Map<Int, String>>(entity.pointsOfInterest)
            } catch (e: Exception) {
                emptyMap()
            }

            AerialMedia(
                uri = Uri.parse(entity.uri),
                type = enumValueOf<AerialMediaType>(entity.type),
                source = enumValueOf<AerialMediaSource>(entity.source),
                metadata = AerialMediaMetadata(
                    shortDescription = entity.shortDescription,
                    pointsOfInterest = pointsMap,
                    timeOfDay = enumValueOf<TimeOfDay>(entity.timeOfDay),
                    scene = enumValueOf<SceneType>(entity.scene),
                    albumName = entity.albumName,
                    title = entity.title,
                    exif = AerialExifMetadata(
                        date = entity.exifDate,
                        offset = entity.exifOffset,
                        latitude = entity.exifLatitude,
                        longitude = entity.exifLongitude,
                        city = entity.exifCity,
                        state = entity.exifState,
                        country = entity.exifCountry,
                        description = entity.exifDescription
                    )
                )
            )
        }

        val musicList = cachedMusic.map { entity ->
            MusicTrack(
                uri = Uri.parse(entity.uri),
                source = enumValueOf<AerialMediaSource>(entity.source)
            )
        }

        val musicPlaylist = if (musicList.isNotEmpty()) {
            MusicPlaylist(
                tracks = musicList,
                shuffle = state.musicShuffleEnabled,
                repeat = state.musicRepeatEnabled
            )
        } else {
            null
        }

        MediaFetchResult(
            mediaPlaylist = MediaPlaylist(mediaList, state.mediaPosition),
            musicPlaylist = musicPlaylist,
            musicResumeIndex = state.musicTrackIndex
        )
    }

    suspend fun cachePlaylist(
        media: List<AerialMedia>,
        musicPlaylist: MusicPlaylist?,
        settingsHash: String,
        shuffleEnabled: Boolean
    ) = withContext(Dispatchers.IO) {
        val music = musicPlaylist?.tracks ?: emptyList()
        val mediaEntities = media.mapIndexed { index, m ->
            val pointsStr = try {
                Json.encodeToString(m.metadata.pointsOfInterest)
            } catch (e: Exception) {
                "{}"
            }
            
            CachedMediaEntity(
                playlistOrder = index,
                uri = m.uri.toString(),
                type = m.type.name,
                source = m.source.name,
                shortDescription = m.metadata.shortDescription,
                pointsOfInterest = pointsStr,
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
                exifDescription = m.metadata.exif.description
            )
        }

        val musicEntities = music.mapIndexed { index, t ->
            CachedMusicTrackEntity(
                playlistOrder = index,
                uri = t.uri.toString(),
                source = t.source.name
            )
        }

        val state = PlaylistStateEntity(
            mediaPosition = -1,
            musicTrackIndex = 0,
            cachedAt = System.currentTimeMillis(),
            settingsHash = settingsHash,
            totalMediaItems = media.size,
            totalMusicTracks = music.size,
            shuffleEnabled = shuffleEnabled,
            musicShuffleEnabled = musicPlaylist?.shuffle ?: false,
            musicRepeatEnabled = musicPlaylist?.repeat ?: false
        )

        dao.clearAll()
        dao.insertMediaItems(mediaEntities)
        dao.insertMusicTracks(musicEntities)
        dao.insertOrUpdateState(state)
    }

    suspend fun saveMediaPosition(position: Int) = withContext(Dispatchers.IO) {
        if (GeneralPrefs.enablePlaylistCache) {
             dao.updateMediaPosition(position)
        }
    }

    suspend fun saveMusicTrackIndex(index: Int) = withContext(Dispatchers.IO) {
        if (GeneralPrefs.enablePlaylistCache) {
             dao.updateMusicTrackIndex(index)
        }
    }

    suspend fun clearCache() = withContext(Dispatchers.IO) {
        dao.clearAll()
    }
}
