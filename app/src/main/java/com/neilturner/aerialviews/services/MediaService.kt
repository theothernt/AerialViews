package com.neilturner.aerialviews.services

import android.content.Context
import com.neilturner.aerialviews.models.MediaPlaylist
import com.neilturner.aerialviews.models.enums.AerialMediaSource
import com.neilturner.aerialviews.models.enums.AerialMediaType
import com.neilturner.aerialviews.models.enums.DescriptionFilenameType
import com.neilturner.aerialviews.models.enums.ProviderSourceType
import com.neilturner.aerialviews.models.enums.TimeOfDay
import com.neilturner.aerialviews.models.prefs.AmazonVideoPrefs
import com.neilturner.aerialviews.models.prefs.AppleVideoPrefs
import com.neilturner.aerialviews.models.prefs.Comm1VideoPrefs
import com.neilturner.aerialviews.models.prefs.Comm2VideoPrefs
import com.neilturner.aerialviews.models.prefs.CustomFeedPrefs
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.models.prefs.ImmichMediaPrefs
import com.neilturner.aerialviews.models.prefs.LocalMediaPrefs
import com.neilturner.aerialviews.models.prefs.SambaMediaPrefs
import com.neilturner.aerialviews.models.prefs.WebDavMediaPrefs
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
import com.neilturner.aerialviews.services.MediaServiceHelper.addFilenameAsDescriptionToMedia
import com.neilturner.aerialviews.services.MediaServiceHelper.addMetadataToManifestVideos
import com.neilturner.aerialviews.services.MediaServiceHelper.buildMediaList
import com.neilturner.aerialviews.utils.TimeOfDayHelper
import com.neilturner.aerialviews.utils.filename
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

class MediaService(
    val context: Context,
) {
    private val providers = mutableListOf<MediaProvider>()

    init {
        providers.add(Comm1MediaProvider(context, Comm1VideoPrefs))
        providers.add(Comm2MediaProvider(context, Comm2VideoPrefs))
        providers.add(AmazonMediaProvider(context, AmazonVideoPrefs))
        providers.add(LocalMediaProvider(context, LocalMediaPrefs))
        providers.add(SambaMediaProvider(context, SambaMediaPrefs))
        providers.add(WebDavMediaProvider(context, WebDavMediaPrefs))
        providers.add(ImmichMediaProvider(context, ImmichMediaPrefs))
        providers.add(AppleMediaProvider(context, AppleVideoPrefs))
        providers.add(CustomFeedProvider(context, CustomFeedPrefs))
        providers.sortBy { it.type == ProviderSourceType.REMOTE }
    }

    suspend fun fetchMedia(): MediaPlaylist =
        withContext(Dispatchers.IO) {
            // Build media list from all providers
            val media = buildMediaList(providers)

            // Split into videos and photos
            var (videos, photos) = media.partition { it.type == AerialMediaType.VIDEO }
            Timber.i("Total media items: ${media.size}, videos ${videos.size}, photos ${photos.size}")

            // Remove duplicates based on filename (with extension!)
            if (GeneralPrefs.removeDuplicates) {
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
            if (GeneralPrefs.ignoreNonManifestVideos) {
                Timber.i("Removing ${unmatchedVideos.size} non-manifest videos")
                unmatchedVideos = emptyList()
            }

            var filteredMedia = unmatchedVideos + matchedVideos + unmatchedPhotos + matchedPhotos

            if (GeneralPrefs.autoTimeOfDay) {
                val currentTimePeriod = TimeOfDayHelper.getCurrentTimePeriod()
                val dayIncludes = GeneralPrefs.playlistTimeOfDayDayIncludes
                val nightIncludes = GeneralPrefs.playlistTimeOfDayNightIncludes
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

            if (GeneralPrefs.shuffleVideos) {
                filteredMedia = filteredMedia.shuffled()
                Timber.i("Shuffling media items")
            }

            Timber.i("Total media items: ${filteredMedia.size}")
            return@withContext MediaPlaylist(filteredMedia)
        }
}
