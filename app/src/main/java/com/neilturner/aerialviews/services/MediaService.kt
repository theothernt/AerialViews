package com.neilturner.aerialviews.services

import android.content.Context
import android.util.Log
import com.neilturner.aerialviews.models.MediaPlaylist
import com.neilturner.aerialviews.models.enums.DescriptionFilenameType
import com.neilturner.aerialviews.models.enums.DescriptionManifestType
import com.neilturner.aerialviews.models.enums.MediaItemType
import com.neilturner.aerialviews.models.enums.ProviderType
import com.neilturner.aerialviews.models.prefs.AppleVideoPrefs
import com.neilturner.aerialviews.models.prefs.Comm1VideoPrefs
import com.neilturner.aerialviews.models.prefs.Comm2VideoPrefs
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.models.prefs.LocalMediaPrefs
import com.neilturner.aerialviews.models.prefs.SambaMediaPrefs
import com.neilturner.aerialviews.models.videos.AerialMedia
import com.neilturner.aerialviews.models.videos.VideoMetadata
import com.neilturner.aerialviews.providers.AppleMediaProvider
import com.neilturner.aerialviews.providers.Comm1MediaProvider
import com.neilturner.aerialviews.providers.Comm2MediaProvider
import com.neilturner.aerialviews.providers.LocalMediaProvider
import com.neilturner.aerialviews.providers.MediaProvider
import com.neilturner.aerialviews.providers.SambaMediaProvider
import com.neilturner.aerialviews.utils.FileHelper
import com.neilturner.aerialviews.utils.filenameWithoutExtension

class MediaService(val context: Context) {
    private val providers = mutableListOf<MediaProvider>()

    init {
        providers.add(Comm1MediaProvider(context, Comm1VideoPrefs))
        providers.add(Comm2MediaProvider(context, Comm2VideoPrefs))
        providers.add(LocalMediaProvider(context, LocalMediaPrefs))
        providers.add(SambaMediaProvider(context, SambaMediaPrefs))
        providers.add(AppleMediaProvider(context, AppleVideoPrefs))
        // Sort by local first so duplicates removed are remote
        providers.sortBy { it.type == ProviderType.REMOTE }
    }

    suspend fun fetchMedia(): MediaPlaylist {
        var media = mutableListOf<AerialMedia>()

        // Find all videos from all providers/sources
        providers.forEach {
            try {
                if (it.enabled) {
                    media.addAll(it.fetchMedia())
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Exception while fetching videos", ex)
            }
        }

        // Remove duplicates based on filename only
        if (GeneralPrefs.removeDuplicates) {
            val numVideos = media.size
            media = media.distinctBy { it.uri.filenameWithoutExtension.lowercase() }.toMutableList()
            Log.i(TAG, "Duplicate videos removed based on filename: ${numVideos - media.size}")
        }

        // Add metadata to (Manifest) Apple, Community videos only
        val manifestDescriptionStyle = GeneralPrefs.descriptionVideoManifestStyle ?: DescriptionManifestType.DISABLED
        val (matched, unmatched) = addMetadataToManifestVideos(media, providers, manifestDescriptionStyle)

        var (videos, photos) = unmatched.partition { it.type == MediaItemType.VIDEO }

        if (!GeneralPrefs.ignoreNonManifestVideos) {
            //addFilenameAsLocation(result.second)
            //media.addAll(result.second)
        }

        // Add description to user videos
        val videoDescriptionStyle = GeneralPrefs.descriptionVideoFilenameStyle ?: DescriptionFilenameType.DISABLED
        videos = addFilenameAsDescriptionToMedia(videos, videoDescriptionStyle)

        // Add description to user images
        val photoDescriptionStyle = GeneralPrefs.descriptionPhotoFilenameStyle ?: DescriptionFilenameType.DISABLED
        photos = addFilenameAsDescriptionToMedia(photos, photoDescriptionStyle)

        // Combine all videos and photos

        // Randomise video order
        if (GeneralPrefs.shuffleVideos) {
            //media.shuffle()
        }

        Log.i(TAG, "Total vids: ${media.size}")
        return MediaPlaylist(media)
    }

    private suspend fun addMetadataToManifestVideos(media: List<AerialMedia>, providers: List<MediaProvider>, description: DescriptionManifestType): Pair<List<AerialMedia>, List<AerialMedia>> {
        val metadata = mutableListOf<VideoMetadata>()
        val matched = mutableListOf<AerialMedia>()
        val unmatched = mutableListOf<AerialMedia>()

        providers.forEach {
            try {
                metadata.addAll((it.fetchMetadata()))
            } catch (ex: Exception) {
                Log.e(TAG, "Exception while fetching metadata", ex)
            }
        }

        // Find video id in metadata list
        media.forEach video@{ video ->
            metadata.forEach { metadata ->
                if (video.type == MediaItemType.VIDEO &&
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

        return Pair(emptyList(), emptyList())
    }

    private fun addFilenameAsDescriptionToMedia(media: List<AerialMedia>, description: DescriptionFilenameType): List<AerialMedia> {
        when (description) {
            DescriptionFilenameType.FILENAME -> {
                media.forEach { item -> item.description = item.uri.filenameWithoutExtension }
            }
            DescriptionFilenameType.FORMATTED -> {
                media.forEach { item -> item.description = FileHelper.filenameToTitleCase(item.uri) }
            }
            DescriptionFilenameType.LAST_FOLDER_FILENAME -> {
                media.forEach { item -> item.description = "Last folder + filename" }
            }
            DescriptionFilenameType.LAST_FOLDERNAME -> {
                media.forEach { item -> item.description = "Last folder name" }
            }
            else -> { /* Do nothing */ }
        }
        return media
    }

    companion object {
        private const val TAG = "VideoService"
    }
}