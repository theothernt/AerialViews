package com.neilturner.aerialviews.services

import android.content.Context
import android.util.Log
import com.neilturner.aerialviews.models.MediaPlaylist
import com.neilturner.aerialviews.models.enums.FilenameAsDescriptionType
import com.neilturner.aerialviews.models.enums.MediaItemType
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
import com.neilturner.aerialviews.utils.filename

class MediaService(val context: Context) {
    private val providers = mutableListOf<MediaProvider>()

    init {
        providers.add(LocalMediaProvider(context, LocalMediaPrefs))
        providers.add(SambaMediaProvider(context, SambaMediaPrefs))
        // Prefer local videos first
        // Remote videos added last so they'll be filtered out if duplicates are found
        providers.add(Comm1MediaProvider(context, Comm1VideoPrefs))
        providers.add(Comm2MediaProvider(context, Comm2VideoPrefs))
        providers.add(AppleMediaProvider(context, AppleVideoPrefs))
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
            media = media.distinctBy { it.uri.filename.lowercase() }.toMutableList()
            Log.i(TAG, "Duplicate videos removed based on filename: ${numVideos - media.size}")
        }

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

    private fun addFilenameAsLocation(media: List<AerialMedia>) {
        // Add filename as video location
        if (GeneralPrefs.filenameAsDescriptionType == FilenameAsDescriptionType.FORMATTED) {
            media.forEach { video ->
                if (video.location.isBlank()) {
                    video.location = FileHelper.filenameToTitleCase(video.uri)
                }
            }
        }
        if (GeneralPrefs.filenameAsDescriptionType == FilenameAsDescriptionType.FILENAME) {
            media.forEach { video ->
                if (video.location.isBlank()) {
                    video.location = FileHelper.filenameToString(video.uri)
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
                    metadata.urls.any { it.contains(video.uri.filename, true) }
                ) {
                    video.location = metadata.location
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
