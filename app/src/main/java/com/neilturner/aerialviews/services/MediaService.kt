package com.neilturner.aerialviews.services

import android.content.Context
import com.neilturner.aerialviews.models.LoadingStatus
import com.neilturner.aerialviews.models.MediaFetchResult
import com.neilturner.aerialviews.models.MediaPlaylist
import com.neilturner.aerialviews.models.enums.AerialMediaSource
import com.neilturner.aerialviews.models.enums.AerialMediaType
import com.neilturner.aerialviews.models.enums.ProviderSourceType
import com.neilturner.aerialviews.models.enums.TimeOfDay
import com.neilturner.aerialviews.models.music.MusicPlaylist
import com.neilturner.aerialviews.models.prefs.AmazonVideoPrefs
import com.neilturner.aerialviews.models.prefs.AppleVideoPrefs
import com.neilturner.aerialviews.models.prefs.Comm1VideoPrefs
import com.neilturner.aerialviews.models.prefs.Comm2VideoPrefs
import com.neilturner.aerialviews.models.prefs.CustomFeedPrefs
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.models.prefs.ImmichMediaPrefs
import com.neilturner.aerialviews.models.prefs.LocalMediaPrefs
import com.neilturner.aerialviews.models.prefs.MusicPrefs
import com.neilturner.aerialviews.models.prefs.SambaMediaPrefs
import com.neilturner.aerialviews.models.prefs.SambaMediaPrefs2
import com.neilturner.aerialviews.models.prefs.WebDavMediaPrefs
import com.neilturner.aerialviews.models.prefs.WebDavMediaPrefs2
import com.neilturner.aerialviews.providers.AmazonMediaProvider
import com.neilturner.aerialviews.providers.AppleMediaProvider
import com.neilturner.aerialviews.providers.Comm1MediaProvider
import com.neilturner.aerialviews.providers.Comm2MediaProvider
import com.neilturner.aerialviews.providers.LocalMediaProvider
import com.neilturner.aerialviews.providers.MediaProvider
import com.neilturner.aerialviews.providers.custom.CustomFeedProvider
import com.neilturner.aerialviews.providers.immich.ImmichMediaProvider
import com.neilturner.aerialviews.providers.samba.SambaMediaProvider
import com.neilturner.aerialviews.providers.webdav.WebDavMediaProvider
import com.neilturner.aerialviews.services.MediaServiceHelper.addMetadataToManifestVideos
import com.neilturner.aerialviews.services.MediaServiceHelper.buildProviderContent
import com.neilturner.aerialviews.utils.TimeOfDayHelper
import com.neilturner.aerialviews.utils.filename
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

class MediaService(
    val context: Context,
    private val providers: MutableList<MediaProvider> = mutableListOf(),
    private val config: Config = Config.fromPreferences(),
) {
    data class Config(
        val removeDuplicates: Boolean,
        val ignoreNonManifestVideos: Boolean,
        val autoTimeOfDay: Boolean,
        val playlistTimeOfDayDayIncludes: Set<String>,
        val playlistTimeOfDayNightIncludes: Set<String>,
        val shuffleVideos: Boolean,
        val shuffleMusic: Boolean,
        val repeatMusic: Boolean,
        val useAppleVideos: Boolean,
        val useAmazonVideos: Boolean,
        val useComm1Videos: Boolean,
        val useComm2Videos: Boolean,
        val useLocalVideos: Boolean,
        val useSambaVideos: Boolean,
        val useWebDavVideos: Boolean,
        val webDavPath: String,
        val useImmichVideos: Boolean,
        val immichUrl: String,
        val immichPath: String,
        val useCustomStreams: Boolean,
        val customUrls: String,
    ) {
        fun buildHash(): String {
            val parts =
                buildList {
                    add(removeDuplicates.toString())
                    add(ignoreNonManifestVideos.toString())
                    add(autoTimeOfDay.toString())
                    add(playlistTimeOfDayDayIncludes.sorted().joinToString(","))
                    add(playlistTimeOfDayNightIncludes.sorted().joinToString(","))
                    add(shuffleVideos.toString())
                    add(shuffleMusic.toString())
                    add(repeatMusic.toString())
                    add(useAppleVideos.toString())
                    add(useAmazonVideos.toString())
                    add(useComm1Videos.toString())
                    add(useComm2Videos.toString())
                    add(useLocalVideos.toString())
                    add(useSambaVideos.toString())
                    add(useWebDavVideos.toString())
                    add(webDavPath)
                    add(useImmichVideos.toString())
                    add(immichUrl)
                    add(immichPath)
                    add(useCustomStreams.toString())
                    add(customUrls)
                }
            return parts.joinToString("|").hashCode().toString()
        }

        companion object {
            fun fromPreferences() =
                Config(
                    removeDuplicates = GeneralPrefs.removeDuplicates,
                    ignoreNonManifestVideos = GeneralPrefs.ignoreNonManifestVideos,
                    autoTimeOfDay = GeneralPrefs.autoTimeOfDay,
                    playlistTimeOfDayDayIncludes = GeneralPrefs.playlistTimeOfDayDayIncludes,
                    playlistTimeOfDayNightIncludes = GeneralPrefs.playlistTimeOfDayNightIncludes,
                    shuffleVideos = GeneralPrefs.shuffleVideos,
                    shuffleMusic = MusicPrefs.shuffle,
                    repeatMusic = MusicPrefs.repeat,
                    useAppleVideos = AppleVideoPrefs.enabled,
                    useAmazonVideos = AmazonVideoPrefs.enabled,
                    useComm1Videos = Comm1VideoPrefs.enabled,
                    useComm2Videos = Comm2VideoPrefs.enabled,
                    useLocalVideos = LocalMediaPrefs.enabled,
                    useSambaVideos = SambaMediaPrefs.enabled || SambaMediaPrefs2.enabled,
                    useWebDavVideos = WebDavMediaPrefs.enabled || WebDavMediaPrefs2.enabled,
                    webDavPath = "${WebDavMediaPrefs.hostName}|${WebDavMediaPrefs.pathName}|${WebDavMediaPrefs2.hostName}|${WebDavMediaPrefs2.pathName}",
                    useImmichVideos = ImmichMediaPrefs.enabled,
                    immichUrl = ImmichMediaPrefs.url,
                    immichPath = ImmichMediaPrefs.pathName,
                    useCustomStreams = CustomFeedPrefs.enabled,
                    customUrls = CustomFeedPrefs.urls,
                )
        }
    }

    init {
        if (providers.isEmpty()) {
            providers.add(Comm1MediaProvider(context, Comm1VideoPrefs))
            providers.add(Comm2MediaProvider(context, Comm2VideoPrefs))
            providers.add(AmazonMediaProvider(context, AmazonVideoPrefs))
            providers.add(LocalMediaProvider(context, LocalMediaPrefs))
            providers.add(SambaMediaProvider(context, SambaMediaPrefs))
            providers.add(SambaMediaProvider(context, SambaMediaPrefs2))
            providers.add(WebDavMediaProvider(context, WebDavMediaPrefs))
            providers.add(WebDavMediaProvider(context, WebDavMediaPrefs2))
            providers.add(ImmichMediaProvider(context, ImmichMediaPrefs))
            providers.add(AppleMediaProvider(context, AppleVideoPrefs))
            providers.add(CustomFeedProvider(context, CustomFeedPrefs))
        }
        providers.sortBy { it.type == ProviderSourceType.REMOTE }
    }

    suspend fun fetchMedia(onStatus: (status: LoadingStatus) -> Unit = {}): MediaFetchResult =
        withContext(Dispatchers.IO) {
            val settingsHash = config.buildHash()
            val cacheRepo =
                com.neilturner.aerialviews.data
                    .PlaylistCacheRepository(context)

            if (GeneralPrefs.enablePlaylistCache) {
                if (cacheRepo.isCacheValid(settingsHash)) {
                    val cached = cacheRepo.getCachedPlaylist()
                    if (cached != null) {
                        onStatus(LoadingStatus.RESUMING)
                        Timber.i("MediaService: USING CACHED PLAYLIST")
                        return@withContext cached
                    } else {
                        Timber.w("MediaService: Cache reported valid but failed to load")
                    }
                } else {
                    Timber.i("MediaService: Cache INVALID or missing, fetching fresh items")
                }
                onStatus(LoadingStatus.BUILDING)
            } else {
                Timber.i("MediaService: Cache DISABLED, fetching fresh items")
                onStatus(LoadingStatus.LOADING)
            }

            val (media, tracks) = buildProviderContent(providers)

            // Split into videos and photos
            var (videos, photos) = media.partition { it.type == AerialMediaType.VIDEO }
            Timber.i("Total media items: ${media.size}, videos ${videos.size}, photos ${photos.size}")

            // Remove duplicates based on filename (with extension!)
            if (config.removeDuplicates) {
                val numVideos = videos.size
                val numPhotos = photos.size
                videos =
                    videos.distinctBy { videos ->
                        if (videos.source != AerialMediaSource.IMMICH) {
                            videos.uri.filename.lowercase()
                        } else {
                            videos.uri
                        }
                    }
                photos =
                    photos.distinctBy { photo ->
                        if (photo.source != AerialMediaSource.IMMICH) {
                            photo.uri.filename.lowercase()
                        } else {
                            photo.uri
                        }
                    }
                Timber.i("Duplicates removed: videos ${numVideos - videos.size}, photos ${numPhotos - photos.size}")
            }

            // Try to match videos with Apple, Community metadata for location/description
            var (matchedVideos, unmatchedVideos) = addMetadataToManifestVideos(videos, providers)
            Timber.i("FeedManifests Videos: matched ${matchedVideos.size}, unmatched ${unmatchedVideos.size}")

            // Split photos in those with metadata and those without
            val (matchedPhotos, unmatchedPhotos) = photos.partition { it.source == AerialMediaSource.IMMICH }
            Timber.i("Photos with metadata: matched ${matchedPhotos.size}, unmatched ${unmatchedPhotos.size}")

            // Discard unmatched manifest videos
            if (config.ignoreNonManifestVideos) {
                Timber.i("Removing ${unmatchedVideos.size} non-manifest videos")
                unmatchedVideos = emptyList()
            }

            var filteredMedia = unmatchedVideos + matchedVideos + unmatchedPhotos + matchedPhotos

            if (config.autoTimeOfDay) {
                val currentTimePeriod = TimeOfDayHelper.getCurrentTimePeriod()
                val dayIncludes = config.playlistTimeOfDayDayIncludes
                val nightIncludes = config.playlistTimeOfDayNightIncludes
                val dayIncludesSunrise = dayIncludes.contains("SUNRISE")
                val dayIncludesSunset = dayIncludes.contains("SUNSET")
                val nightIncludesSunrise = nightIncludes.contains("SUNRISE")
                val nightIncludesSunset = nightIncludes.contains("SUNSET")
                Timber.i("Applying auto time-of-day filtering for period: $currentTimePeriod")
                val originalMedia = filteredMedia
                filteredMedia =
                    filteredMedia.filter { media ->
                        if (media.type == AerialMediaType.VIDEO) {
                            when (currentTimePeriod) {
                                TimeOfDay.DAY -> {
                                    media.metadata.timeOfDay == TimeOfDay.DAY ||
                                        (dayIncludesSunrise && media.metadata.timeOfDay == TimeOfDay.SUNRISE) ||
                                        (dayIncludesSunset && media.metadata.timeOfDay == TimeOfDay.SUNSET) ||
                                        media.metadata.timeOfDay == TimeOfDay.UNKNOWN
                                }

                                TimeOfDay.NIGHT -> {
                                    (nightIncludesSunrise && media.metadata.timeOfDay == TimeOfDay.SUNRISE) ||
                                        (nightIncludesSunset && media.metadata.timeOfDay == TimeOfDay.SUNSET) ||
                                        media.metadata.timeOfDay == TimeOfDay.NIGHT ||
                                        media.metadata.timeOfDay == TimeOfDay.UNKNOWN
                                }

                                else -> {
                                    true
                                }
                            }
                        } else {
                            true
                        }
                    }

                val remainingVideos = filteredMedia.count { it.type == AerialMediaType.VIDEO }
                if (remainingVideos == 0) {
                    Timber.w("Auto time-of-day filtering removed all videos; falling back to unfiltered media")
                    filteredMedia = originalMedia
                }
                Timber.i("Media items after time filtering: ${filteredMedia.size}")
            }

            if (config.shuffleVideos) {
                filteredMedia = filteredMedia.shuffled()
                Timber.i("Shuffling media items")
            }

            val musicPlaylist =
                tracks
                    .takeIf { it.isNotEmpty() }
                    ?.let { availableTracks ->
                        val orderedTracks = if (config.shuffleMusic) availableTracks.shuffled() else availableTracks
                        MusicPlaylist(
                            tracks = orderedTracks,
                            shuffle = config.shuffleMusic,
                            repeat = config.repeatMusic,
                        )
                    }

            Timber.i("Total media items: ${filteredMedia.size}")

            if (GeneralPrefs.enablePlaylistCache) {
                // Cache enabled: save to DB, return windowed playlist that streams from DB
                cacheRepo.cachePlaylist(
                    media = filteredMedia,
                    musicPlaylist = musicPlaylist,
                    settingsHash = settingsHash,
                    shuffleEnabled = config.shuffleVideos,
                )

                val cachedResult = cacheRepo.getCachedPlaylist()
                if (cachedResult != null) {
                    Timber.i("MediaService: Fresh playlist cached and loaded from DB (${filteredMedia.size} items)")
                    return@withContext cachedResult
                }
                Timber.w("MediaService: Failed to read back cached playlist, falling back to in-memory")
            } else {
                Timber.i("MediaService: Cache disabled, using full in-memory playlist (${filteredMedia.size} items)")
            }

            // Cache disabled or cache read-back failed: all items in memory, no DB
            return@withContext MediaFetchResult(
                mediaPlaylist = MediaPlaylist(filteredMedia),
                musicPlaylist = musicPlaylist,
            )
        }
}
