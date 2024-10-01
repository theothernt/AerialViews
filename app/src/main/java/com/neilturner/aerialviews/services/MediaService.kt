package com.neilturner.aerialviews.services

import android.content.Context
import android.net.Uri
import com.neilturner.aerialviews.models.MediaPlaylist
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
import com.neilturner.aerialviews.models.videos.VideoMetadata
import com.neilturner.aerialviews.providers.AppleMediaProvider
import com.neilturner.aerialviews.providers.Comm1MediaProvider
import com.neilturner.aerialviews.providers.Comm2MediaProvider
import com.neilturner.aerialviews.providers.ImmichMediaProvider
import com.neilturner.aerialviews.providers.LocalMediaProvider
import com.neilturner.aerialviews.providers.MediaProvider
import com.neilturner.aerialviews.providers.SambaMediaProvider
import com.neilturner.aerialviews.providers.WebDavMediaProvider
import com.neilturner.aerialviews.utils.FileHelper
import com.neilturner.aerialviews.utils.filenameWithoutExtension
import timber.log.Timber

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
        // Sort by local first so duplicates removed are remote
        providers.sortBy { it.type == ProviderSourceType.REMOTE }
    }

    suspend fun fetchMedia(): MediaPlaylist {
        // Find all videos from all providers/sources
        var media = mutableListOf<AerialMedia>()
        providers.forEach {
            try {
                if (it.enabled) {
                    media.addAll(it.fetchMedia())
                }
            } catch (ex: Exception) {
                Timber.e(ex, "Exception while fetching videos")
            }
        }

        // Remove duplicates based on filename only
        if (GeneralPrefs.removeDuplicates) {

            // Populate the filename field so that we can remove duplicates for providers for which
            // the asset filename is not at the end of the URI.
            media.forEach {
                if (it.filename == Uri.EMPTY) {
                    it.filename = it.uri
                }
            }

            val numVideos = media.size
            media = media.distinctBy { it.uri.filenameWithoutExtension.lowercase() }.toMutableList()
            Timber.i("Duplicate videos removed based on filename: ${numVideos - media.size}")
        }

        // Add metadata to (Manifest) Apple, Community videos only
        val manifestDescriptionStyle = GeneralPrefs.descriptionVideoManifestStyle ?: DescriptionManifestType.DISABLED
        val (matched, unmatched) = addMetadataToManifestVideos(media, providers, manifestDescriptionStyle)
        Timber.i("Manifest: matched ${matched.size}, unmatched ${unmatched.size}")

        var (videos, photos) = unmatched.partition { it.type == AerialMediaType.VIDEO }
        Timber.i("Unmatched: videos ${videos.size}, photos ${photos.size}")

        // Remove if not Apple or Community videos
        if (GeneralPrefs.ignoreNonManifestVideos) {
            videos = listOf()
            Timber.i("Removing non-manifest videos")
        }

        // Add description to user videos
        val videoDescriptionStyle = GeneralPrefs.descriptionVideoFilenameStyle ?: DescriptionFilenameType.DISABLED
        videos = addFilenameAsDescriptionToMedia(videos, videoDescriptionStyle)

        // Add description to user images
        val photoDescriptionStyle = GeneralPrefs.descriptionPhotoFilenameStyle ?: DescriptionFilenameType.DISABLED
        photos = addFilenameAsDescriptionToMedia(photos, photoDescriptionStyle)

        // Combine all videos and photos
        var filteredMedia = matched + videos + photos

        // Randomise video order
        if (GeneralPrefs.shuffleVideos) {
            filteredMedia = filteredMedia.shuffled()
            Timber.i("Shuffling media items")
        }

        Timber.i("Total media items: ${filteredMedia.size}")
        return MediaPlaylist(filteredMedia)
    }

    private suspend fun addMetadataToManifestVideos(
        media: List<AerialMedia>,
        providers: List<MediaProvider>,
        description: DescriptionManifestType,
    ): Pair<List<AerialMedia>, List<AerialMedia>> {
        val metadata = mutableListOf<VideoMetadata>()
        val matched = mutableListOf<AerialMedia>()
        val unmatched = mutableListOf<AerialMedia>()

        providers.forEach {
            try {
                metadata.addAll((it.fetchMetadata()))
            } catch (ex: Exception) {
                Timber.e(ex, "Exception while fetching metadata")
            }
        }

        // Find video id in metadata list
        media.forEach video@{ video ->
            if (video.description.isNotEmpty() || video.poi.isNotEmpty()) {
                matched.add(video)
                return@video
            }
            metadata.forEach { metadata ->
                if (video.type == AerialMediaType.VIDEO &&
                    metadata.urls.any { it.contains(video.uri.filenameWithoutExtension, true) }
                ) {
                    if (description != DescriptionManifestType.DISABLED) {
                        video.description = metadata.description
                        video.poi = metadata.poi
                    }
                    matched.add(video)
                    return@video
                }
            }
            unmatched.add(video)
        }
        return Pair(matched, unmatched)
    }

    private fun addFilenameAsDescriptionToMedia(
        media: List<AerialMedia>,
        description: DescriptionFilenameType,
    ): List<AerialMedia> {
        when (description) {
            DescriptionFilenameType.FILENAME -> {
                media.forEach { item ->
                    if (item.filename == Uri.EMPTY) {
                        item.description = item.uri.filenameWithoutExtension
                    } else {
                        item.description = item.filename.toString()
                    }
                }
            }
            DescriptionFilenameType.LAST_FOLDER_FILENAME -> {
                media.forEach { item -> item.description = FileHelper.folderAndFilenameFromUri(item.uri, true) }
            }
            DescriptionFilenameType.LAST_FOLDER_NAME -> {
                media.forEach { item -> item.description = FileHelper.folderAndFilenameFromUri(item.uri) }
            }
            else -> { /* Do nothing */ }
        }
        return media
    }
}
