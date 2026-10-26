package com.neilturner.aerialviews.services

import android.content.Context
import android.os.Bundle
import com.neilturner.aerialviews.data.PlaylistCacheRepository
import com.neilturner.aerialviews.data.network.NetworkHelper
import com.neilturner.aerialviews.models.LoadingStatus
import com.neilturner.aerialviews.models.MediaFetchResult
import com.neilturner.aerialviews.models.MediaPlaylist
import com.neilturner.aerialviews.models.TimeOfDayFilter
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
import com.neilturner.aerialviews.models.prefs.NCMemoriesMediaPrefs
import com.neilturner.aerialviews.models.prefs.SambaMediaPrefs
import com.neilturner.aerialviews.models.prefs.SambaMediaPrefs2
import com.neilturner.aerialviews.models.prefs.WebDavMediaPrefs
import com.neilturner.aerialviews.models.prefs.WebDavMediaPrefs2
import com.neilturner.aerialviews.models.videos.AerialMedia
import com.neilturner.aerialviews.providers.AmazonMediaProvider
import com.neilturner.aerialviews.providers.AppleMediaProvider
import com.neilturner.aerialviews.providers.Comm1MediaProvider
import com.neilturner.aerialviews.providers.Comm2MediaProvider
import com.neilturner.aerialviews.providers.LocalMediaProvider
import com.neilturner.aerialviews.providers.MediaProvider
import com.neilturner.aerialviews.providers.custom.CustomFeedProvider
import com.neilturner.aerialviews.providers.immich.ImmichMediaProvider
import com.neilturner.aerialviews.providers.ncmemories.NCMemoriesMediaProvider
import com.neilturner.aerialviews.providers.samba.SambaMediaProvider
import com.neilturner.aerialviews.providers.webdav.WebDavMediaProvider
import com.neilturner.aerialviews.services.MediaServiceHelper.addMetadataToManifestVideos
import com.neilturner.aerialviews.services.MediaServiceHelper.buildProviderContent
import com.neilturner.aerialviews.services.MediaServiceHelper.weightedInterleavedShuffle
import com.neilturner.aerialviews.utils.FirebaseHelper
import com.neilturner.aerialviews.utils.filename
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

class MediaService(
    val context: Context,
    private val providers: MutableList<MediaProvider> = mutableListOf(),
    private val config: Config = Config.fromPreferences(),
    private val hashFn: (() -> String)? = null,
    private val periodProvider: () -> TimeOfDay = { TimeOfDayHelper.getCurrentTimePeriod() },
) {
    data class Config(
        val removeDuplicates: Boolean,
        val ignoreNonManifestVideos: Boolean,
        val autoTimeOfDay: Boolean,
        val playlistTimeOfDayDayIncludes: Set<String>,
        val playlistTimeOfDayNightIncludes: Set<String>,
        val playlistCache: Boolean,
        val shuffleVideos: Boolean,
        val shuffleMusic: Boolean,
        val repeatMusic: Boolean,
        val wifiOnly: Boolean = false,
    ) {
        companion object {
            fun fromPreferences() =
                Config(
                    removeDuplicates = GeneralPrefs.removeDuplicates,
                    ignoreNonManifestVideos = GeneralPrefs.ignoreNonManifestVideos,
                    autoTimeOfDay = GeneralPrefs.autoTimeOfDay,
                    playlistTimeOfDayDayIncludes = GeneralPrefs.playlistTimeOfDayDayIncludes,
                    playlistTimeOfDayNightIncludes = GeneralPrefs.playlistTimeOfDayNightIncludes,
                    playlistCache = GeneralPrefs.playlistCache,
                    shuffleVideos = GeneralPrefs.shuffleVideos,
                    shuffleMusic = MusicPrefs.shuffle,
                    repeatMusic = MusicPrefs.repeat,
                    wifiOnly = GeneralPrefs.wifiOnly,
                )
        }
    }

    private fun buildCompositeHash(filterRemote: Boolean): String =
        PlaylistSettingsHash.build(
            settings =
                PlaylistSettingsHash.Settings(
                    removeDuplicates = GeneralPrefs.removeDuplicates,
                    ignoreNonManifestVideos = GeneralPrefs.ignoreNonManifestVideos,
                    shuffleVideos = GeneralPrefs.shuffleVideos,
                    shuffleMusic = MusicPrefs.shuffle,
                    repeatMusic = MusicPrefs.repeat,
                    filterRemote = filterRemote,
                ),
            providerHashes = providers.map { "${it.type}:${it.settingsHash()}" },
        )

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
            providers.add(NCMemoriesMediaProvider(context, NCMemoriesMediaPrefs))
            providers.add(AppleMediaProvider(context, AppleVideoPrefs))
            providers.add(CustomFeedProvider(context, CustomFeedPrefs))
        }
        providers.sortBy { it.type == ProviderSourceType.REMOTE }
    }

    suspend fun fetchMedia(onStatus: (status: LoadingStatus) -> Unit = {}): MediaFetchResult =
        withContext(Dispatchers.IO) {
            val networkType = NetworkHelper.getNetworkType(context)
            val filterRemote = config.wifiOnly && !NetworkHelper.isOnWifiOrEthernet(context)
            Timber.i("MediaService: Network: $networkType | WiFi-only mode: ${config.wifiOnly} | Filtering remote sources: $filterRemote")

            val settingsHash = hashFn?.invoke() ?: buildCompositeHash(filterRemote)
            val cacheRepo =
                if (config.playlistCache) {
                    PlaylistCacheRepository(context)
                } else {
                    null
                }

            // Resolve the period once: the same filter drives the cache lookup, the fallback read
            // and, when caching is off, the in-memory list.
            val filter =
                if (config.autoTimeOfDay) {
                    TimeOfDayFilter.forPeriod(
                        period = periodProvider(),
                        dayIncludes = config.playlistTimeOfDayDayIncludes,
                        nightIncludes = config.playlistTimeOfDayNightIncludes,
                    )
                } else {
                    TimeOfDayFilter.ALL
                }

            if (config.playlistCache) {
                if (cacheRepo != null && cacheRepo.isCacheValid(settingsHash, filter)) {
                    val cached = cacheRepo.getCachedPlaylist(filter)
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

            // Excluded before anything is fetched, so remote providers are never contacted over
            // cellular. The cache is written after this filter, so its size, chunk offsets and
            // saved position all stay in a single index space.
            val activeProviders =
                if (filterRemote) {
                    Timber.i("MediaService: WiFi-only mode enabled, excluding REMOTE providers (not on WiFi/Ethernet)")
                    providers.filter { it.type != ProviderSourceType.REMOTE }
                } else {
                    providers
                }

            val (media, tracks) = buildProviderContent(activeProviders)

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
            var (matchedVideos, unmatchedVideos) = addMetadataToManifestVideos(videos, activeProviders)
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

            // With caching on, the full list is stored and the filter is applied at read time, so
            // the cache survives day/night cycles. Without a cache the filter is applied here,
            // before shuffling, exactly as it always was.
            if (cacheRepo == null && filter.enabled) {
                Timber.i("Applying auto time-of-day filtering for period: ${filter.period}")
                val before = filteredMedia.size
                filteredMedia = filter.apply(filteredMedia)
                Timber.i("Media items after time filtering: ${filteredMedia.size} (was $before)")
            }

            if (config.shuffleVideos) {
                filteredMedia = weightedInterleavedShuffle(filteredMedia)
                Timber.i("Shuffling media items with weighted source interleaving")
            }

            // Defence in depth for a provider that emits a network source despite being typed
            // local. Applied before caching so the cached list only ever holds playable items.
            if (filterRemote) {
                val before = filteredMedia.size
                filteredMedia = filteredMedia.filterNot { it.source.requiresNetwork }
                Timber.i("MediaService: WiFi-only filter removed ${before - filteredMedia.size} remote items")
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

            // Track enabled media sources and media counts. With caching on, analytics should
            // still reflect what would be played now rather than the full cached library.
            trackMediaUsage(filter.apply(filteredMedia))

            if (config.playlistCache && cacheRepo != null) {
                // Cache enabled: store the full list, then return a windowed playlist that reads
                // back through the current time-of-day filter.
                cacheRepo.cachePlaylist(
                    media = filteredMedia,
                    musicPlaylist = musicPlaylist,
                    settingsHash = settingsHash,
                    shuffleEnabled = config.shuffleVideos,
                )

                val cachedResult = cacheRepo.getCachedPlaylist(filter)
                if (cachedResult != null) {
                    Timber.i("MediaService: Fresh playlist cached and loaded from DB (${filteredMedia.size} items)")
                    return@withContext cachedResult
                }
                Timber.w("MediaService: Failed to read back cached playlist, falling back to in-memory")
            } else {
                Timber.i("MediaService: Cache disabled, using full in-memory playlist (${filteredMedia.size} items)")
            }

            return@withContext MediaFetchResult(
                mediaPlaylist = MediaPlaylist(filter.apply(filteredMedia)),
                musicPlaylist = musicPlaylist,
            )
        }

    private fun trackMediaUsage(media: List<AerialMedia>) {
        // Count distinct sources
        val sourceCount = media.map { it.source }.distinct().size

        // Count videos and photos
        val videoCount = media.count { it.type == AerialMediaType.VIDEO }
        val photoCount = media.count { it.type == AerialMediaType.IMAGE }

        val bundle = Bundle()
        bundle.putString("total_videos", generalizeCount(videoCount))
        bundle.putString("total_photos", generalizeCount(photoCount))
        bundle.putString("total_sources", sourceCount.toString())

        FirebaseHelper.analyticsEvent(
            "media_sources_usage",
            bundle,
        )

        Timber.d("Media usage tracked: ${bundle.keySet().joinToString(", ") { "$it=${bundle.getString(it)}" }}")
    }

    private fun generalizeCount(count: Int): String =
        when {
            count == 0 -> "0"

            count < 10 -> "<10"

            count < 100 -> "${(count / 10) * 10}"

            // Round to nearest 10
            count < 1000 -> "${(count / 100) * 100}"

            // Round to nearest 100
            else -> "${(count / 1000) * 1000}" // Round to nearest 1000
        }
}
