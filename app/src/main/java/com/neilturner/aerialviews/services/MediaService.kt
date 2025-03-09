package com.neilturner.aerialviews.services

import android.content.Context
import com.neilturner.aerialviews.models.MediaPlaylist
import com.neilturner.aerialviews.models.enums.AerialMediaSource
import com.neilturner.aerialviews.models.enums.AerialMediaType
import com.neilturner.aerialviews.models.enums.DescriptionFilenameType
import com.neilturner.aerialviews.models.enums.DescriptionManifestType
import com.neilturner.aerialviews.models.enums.ProviderSourceType
import com.neilturner.aerialviews.models.prefs.AppleVideoPrefs
import com.neilturner.aerialviews.models.prefs.Comm1VideoPrefs
import com.neilturner.aerialviews.models.prefs.Comm2VideoPrefs
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.models.prefs.ImmichMediaPrefs
import com.neilturner.aerialviews.models.prefs.LocalMediaPrefs
import com.neilturner.aerialviews.models.prefs.SambaMediaPrefs
import com.neilturner.aerialviews.models.prefs.WebDavMediaPrefs
import com.neilturner.aerialviews.models.videos.AerialMedia
import com.neilturner.aerialviews.providers.AppleMediaProvider
import com.neilturner.aerialviews.providers.Comm1MediaProvider
import com.neilturner.aerialviews.providers.Comm2MediaProvider
import com.neilturner.aerialviews.providers.ImmichMediaProvider
import com.neilturner.aerialviews.providers.LocalMediaProvider
import com.neilturner.aerialviews.providers.MediaProvider
import com.neilturner.aerialviews.providers.SambaMediaProvider
import com.neilturner.aerialviews.providers.WebDavMediaProvider
import com.neilturner.aerialviews.utils.FileHelper
import com.neilturner.aerialviews.utils.filename
import com.neilturner.aerialviews.utils.filenameWithoutExtension
import com.neilturner.aerialviews.utils.parallelForEachCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class MediaService(
    val context: Context,
) {
    private val providers = mutableListOf<MediaProvider>()

    init {
        providers.add(Comm1MediaProvider(context, Comm1VideoPrefs))
        providers.add(Comm2MediaProvider(context, Comm2VideoPrefs))
        providers.add(LocalMediaProvider(context, LocalMediaPrefs))
        providers.add(SambaMediaProvider(context, SambaMediaPrefs))
        providers.add(WebDavMediaProvider(context, WebDavMediaPrefs))
        providers.add(ImmichMediaProvider(context, ImmichMediaPrefs))
        providers.add(AppleMediaProvider(context, AppleVideoPrefs))
        providers.sortBy { it.type == ProviderSourceType.REMOTE }
    }

    suspend fun fetchMedia(): MediaPlaylist =
        withContext(Dispatchers.IO) {
            // Build media list from all providers
            val media = buildMediaList()

            // Split into videos and photos
            var (videos, photos) = media.partition { it.type == AerialMediaType.VIDEO }
            Timber.i("Total media items: ${media.size}, videos ${videos.size}, photos ${photos.size}")

            // Remove duplicates based on filename (with extension!)
            if (GeneralPrefs.removeDuplicates) {
                val numVideos = videos.size
                val numPhotos = photos.size
                videos = videos.distinctBy { it.uri.filename.lowercase() }
                // Except for Immich as filenames are random
                photos = photos.distinctBy { Pair(it.uri.filename.lowercase(), it.source != AerialMediaSource.IMMICH) }
                Timber.i("Duplicates removed: videos ${numVideos - videos.size}, photos ${numPhotos - photos.size}")
            }

            // Try to match videos with Apple, Community metadata for location/description
            val manifestDescriptionStyle = GeneralPrefs.descriptionVideoManifestStyle ?: DescriptionManifestType.DISABLED
            var (matchedVideos, unmatchedVideos) = addMetadataToManifestVideos(videos, providers, manifestDescriptionStyle)
            Timber.i("Manifest Videos: matched ${matchedVideos.size}, unmatched ${unmatchedVideos.size}")

            // Split photos in those with metadata and those without
            var (matchedPhotos, unmatchedPhotos) = photos.partition { it.source == AerialMediaSource.IMMICH }
            Timber.i("Photos with metadata: matched ${matchedPhotos.size}, unmatched ${unmatchedPhotos.size}")

            // Discard unmatched manifest videos
            if (GeneralPrefs.ignoreNonManifestVideos) {
                Timber.i("Removing ${unmatchedVideos.size} non-manifest videos")
                unmatchedVideos = emptyList()
            }

            // Unmatched videos can have their filename added as a description
            val videoDescriptionStyle = GeneralPrefs.descriptionVideoFilenameStyle ?: DescriptionFilenameType.DISABLED
            if (videoDescriptionStyle != DescriptionFilenameType.DISABLED) {
                var videoPathDepth = GeneralPrefs.descriptionVideoFolderLevel.toIntOrNull() ?: 1
                unmatchedVideos = addFilenameAsDescriptionToMedia(unmatchedVideos, videoDescriptionStyle, videoPathDepth)
            }

            val photoDescriptionStyle = GeneralPrefs.descriptionPhotoFilenameStyle ?: DescriptionFilenameType.DISABLED
            if (photoDescriptionStyle != DescriptionFilenameType.DISABLED) {
                var photoPathDepth = GeneralPrefs.descriptionPhotoFolderLevel.toIntOrNull() ?: 1
                unmatchedPhotos = addFilenameAsDescriptionToMedia(unmatchedPhotos, photoDescriptionStyle, photoPathDepth)
            }

            var filteredMedia = unmatchedVideos + matchedVideos + unmatchedPhotos + matchedPhotos

            if (GeneralPrefs.shuffleVideos) {
                filteredMedia = filteredMedia.shuffled()
                Timber.i("Shuffling media items")
            }

            Timber.i("Total media items: ${filteredMedia.size}")
            return@withContext MediaPlaylist(filteredMedia)
        }

    private suspend fun addMetadataToManifestVideos(
        media: List<AerialMedia>,
        providers: List<MediaProvider>,
        description: DescriptionManifestType,
    ): Pair<List<AerialMedia>, List<AerialMedia>> {
        val metadata = ConcurrentHashMap<String, Pair<String, Map<Int, String>>>()
        val matched = CopyOnWriteArrayList<AerialMedia>()
        val unmatched = CopyOnWriteArrayList<AerialMedia>()

        providers.forEach {
            try {
                metadata.putAll(it.fetchMetadata())
            } catch (ex: Exception) {
                Timber.e(ex, "Exception while fetching metadata")
            }
        }

        media.parallelForEachCompat video@{ video ->
            val data = metadata.get(video.uri.filenameWithoutExtension.lowercase())
            if (data != null) {
                if (description != DescriptionManifestType.DISABLED) {
                    video.description = data.first
                    video.poi = data.second
                }
                matched.add(video)
                return@video
            }
            unmatched.add(video)
        }

        return Pair(matched, unmatched)
    }

    private fun addFilenameAsDescriptionToMedia(
        media: List<AerialMedia>,
        description: DescriptionFilenameType,
        pathDepth: Int,
    ): List<AerialMedia> {
        when (description) {
            DescriptionFilenameType.FILENAME -> {
                media.parallelForEachCompat { item ->
                    item.description = item.uri.filenameWithoutExtension
                }
            }
            DescriptionFilenameType.LAST_FOLDER_FILENAME -> {
                media.parallelForEachCompat { item ->
                    item.description = FileHelper.formatFolderAndFilenameFromUri(item.uri, true, pathDepth)
                }
            }
            DescriptionFilenameType.LAST_FOLDER_NAME -> {
                media.parallelForEachCompat { item ->
                    item.description = FileHelper.formatFolderAndFilenameFromUri(item.uri, false, pathDepth)
                }
            }
            else -> { /* Do nothing */ }
        }
        return media
    }

    private fun buildMediaList(): List<AerialMedia> {
        var media = CopyOnWriteArrayList<AerialMedia>()

        providers
            .filter { it.enabled == true }
            .parallelForEachCompat {
                runBlocking {
                    media.addAll(it.fetchMedia())
                }
            }
        return media
    }
}
