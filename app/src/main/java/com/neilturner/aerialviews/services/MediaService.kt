package com.neilturner.aerialviews.services

import android.content.Context
import android.util.Log
import com.neilturner.aerialviews.models.MediaPlaylist
import com.neilturner.aerialviews.models.enums.FilenameAsDescriptionType
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

        //  Add metadata to (Manifest) Apple, Community videos only
        // split videos into matched and unmatched

        val matched = emptyList<AerialMedia>()
        val unmatched = emptyList<AerialMedia>()

        var (videos, photos) = unmatched.partition { it.type == MediaItemType.VIDEO }

        // Add description to user videos
        val videoDescriptionStyle = GeneralPrefs.descriptionVideoFilenameStyle ?: FilenameAsDescriptionType.DISABLED
        videos = addFilenameAsDescriptionToMedia(videos, videoDescriptionStyle)

        // Add description to user images
        val photoDescriptionStyle = GeneralPrefs.descriptionPhotoFilenameStyle ?: FilenameAsDescriptionType.DISABLED
        photos = addFilenameAsDescriptionToMedia(photos, photoDescriptionStyle)



        // Add metadata to videos for filtering matched and unmatched
        val result = addMetadataToVideos(media, providers)
        media = result.first.toMutableList()

        // Add unmatched videos
        if (!GeneralPrefs.ignoreNonManifestVideos) {
            addFilenameAsLocation(result.second)
            media.addAll(result.second)
        }

        // Randomise video order
        if (GeneralPrefs.shuffleVideos) {
            media.shuffle()
        }

        Log.i(TAG, "Total vids: ${media.size}")
        return MediaPlaylist(media)
    }

    private fun addMetadataToManifestVideos(media: List<AerialMedia>, providers: List<MediaProvider>): Pair<List<AerialMedia>, List<AerialMedia>> {
        return Pair(emptyList(), emptyList())
    }

    private fun addFilenameAsDescriptionToMedia(media: List<AerialMedia>, description: FilenameAsDescriptionType): List<AerialMedia> {
        when (description) {
            FilenameAsDescriptionType.FILENAME -> {
                media.forEach { item -> item.description = item.uri.filenameWithoutExtension }
            }
            FilenameAsDescriptionType.FORMATTED -> {
                media.forEach { item -> item.description = FileHelper.filenameToTitleCase(item.uri) }
            }
            FilenameAsDescriptionType.LAST_FOLDER_FILENAME -> {
                media.forEach { item -> item.description = "Last folder + filename" }
            }
            FilenameAsDescriptionType.LAST_FOLDERNAME -> {
                media.forEach { item -> item.description = "Last folder name" }
            }
            else -> { /* Do nothing */ }
        }
        return media
    }

    private fun addFilenameAsLocation(media: List<AerialMedia>) {
        // Add filename as video location
        if (GeneralPrefs.descriptionVideoFilenameStyle == FilenameAsDescriptionType.FORMATTED) {
            media.forEach { video ->
                if (video.description.isBlank()) {
                    video.description = FileHelper.filenameToTitleCase(video.uri)
                }
            }
        }
        if (GeneralPrefs.descriptionVideoFilenameStyle == FilenameAsDescriptionType.FILENAME) {
            media.forEach { video ->
                if (video.description.isBlank()) {
                    //video.description = FileHelper.filenameToString(video.uri)
                }
            }
        }
    }

    private suspend fun addMetadataToVideos(media: List<AerialMedia>, providers: List<MediaProvider>): Pair<List<AerialMedia>, List<AerialMedia>> {
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
                if (video.type != MediaItemType.IMAGE &&
                    metadata.urls.any { it.contains(video.uri.filenameWithoutExtension, true) }
                ) {
                    video.description = metadata.description
                    video.poi = metadata.poi
                    matched.add(video)
                    return@video
                }
            }
            unmatched.add(video)
        }
        return Pair(matched, unmatched)
    }

    companion object {
        private const val TAG = "VideoService"
    }
}