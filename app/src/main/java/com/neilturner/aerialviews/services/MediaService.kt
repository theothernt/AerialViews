package com.neilturner.aerialviews.services

import android.content.Context
import android.os.Build
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
import com.neilturner.aerialviews.utils.filenameWithoutExtension
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
            var media = buildMediaList()
            Timber.i("Total media items: ${media.size}")

            if (GeneralPrefs.removeDuplicates) {
                val numVideos = media.size
                media =
                    media
                        .distinctBy {
                            when (it.source) {
                                AerialMediaSource.IMMICH -> it.uri.toString()
                                else -> Pair(it.uri.filenameWithoutExtension.lowercase(), it.type)
                            }
                        }.toMutableList()
                Timber.i("Duplicate videos removed: ${numVideos - media.size}")
            }

            val manifestDescriptionStyle = GeneralPrefs.descriptionVideoManifestStyle ?: DescriptionManifestType.DISABLED
            val (matched, unmatched) = addMetadataToManifestVideos(media, providers, manifestDescriptionStyle)
            Timber.i("Manifest: matched ${matched.size}, unmatched ${unmatched.size}")

            var (videos, photos) = unmatched.partition { it.type == AerialMediaType.VIDEO }
            Timber.i("Unmatched: videos ${videos.size}, photos ${photos.size}")

            if (GeneralPrefs.ignoreNonManifestVideos) {
                videos = listOf()
                Timber.i("Removing non-manifest videos")
            }

            val videoDescriptionStyle = GeneralPrefs.descriptionVideoFilenameStyle ?: DescriptionFilenameType.DISABLED
            videos = addFilenameAsDescriptionToMedia(videos, videoDescriptionStyle)

            val photoDescriptionStyle = GeneralPrefs.descriptionPhotoFilenameStyle ?: DescriptionFilenameType.DISABLED
            photos = addFilenameAsDescriptionToMedia(photos, photoDescriptionStyle)

            var filteredMedia = matched + videos + photos

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

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            media.forEach video@{ video ->
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
        } else {
            media.parallelStream().forEach video@{ video ->
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
        }

        return Pair(matched, unmatched)
    }

    private fun addFilenameAsDescriptionToMedia(
        media: List<AerialMedia>,
        description: DescriptionFilenameType,
    ): List<AerialMedia> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            when (description) {
                DescriptionFilenameType.FILENAME -> {
                    media.forEach { item -> item.description = item.uri.filenameWithoutExtension }
                }
                DescriptionFilenameType.LAST_FOLDER_FILENAME -> {
                    media.forEach { item -> item.description = FileHelper.folderAndFilenameFromUri(item.uri, true) }
                }
                DescriptionFilenameType.LAST_FOLDER_NAME -> {
                    media.forEach { item -> item.description = FileHelper.folderAndFilenameFromUri(item.uri) }
                }
                else -> { /* Do nothing */ }
            }
        } else {
            when (description) {
                DescriptionFilenameType.FILENAME -> {
                    media.parallelStream().forEach { item -> item.description = item.uri.filenameWithoutExtension }
                }
                DescriptionFilenameType.LAST_FOLDER_FILENAME -> {
                    media.parallelStream().forEach { item -> item.description = FileHelper.folderAndFilenameFromUri(item.uri, true) }
                }
                DescriptionFilenameType.LAST_FOLDER_NAME -> {
                    media.parallelStream().forEach { item -> item.description = FileHelper.folderAndFilenameFromUri(item.uri) }
                }
                else -> { /* Do nothing */ }
            }
        }

        return media
    }

    private suspend fun buildMediaList(): List<AerialMedia> {
        var media = CopyOnWriteArrayList<AerialMedia>()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            providers.forEach {
                try {
                    if (it.enabled) {
                        media.addAll(it.fetchMedia())
                    }
                } catch (ex: Exception) {
                    Timber.e(ex, "Exception while fetching videos")
                }
            }
        } else {
            providers
                .parallelStream()
                .filter { it.enabled }
                .forEach {
                    try {
                        runBlocking {
                            media.addAll(it.fetchMedia())
                        }
                    } catch (ex: Exception) {
                        Timber.e(ex, "Exception while fetching videos")
                    }
                }
        }
        return media
    }
}
