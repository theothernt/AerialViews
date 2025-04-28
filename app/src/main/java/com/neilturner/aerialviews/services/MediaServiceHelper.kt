package com.neilturner.aerialviews.services

import com.neilturner.aerialviews.models.enums.DescriptionFilenameType
import com.neilturner.aerialviews.models.enums.DescriptionManifestType
import com.neilturner.aerialviews.models.videos.AerialMedia
import com.neilturner.aerialviews.providers.MediaProvider
import com.neilturner.aerialviews.utils.FileHelper
import com.neilturner.aerialviews.utils.FirebaseHelper
import com.neilturner.aerialviews.utils.filenameWithoutExtension
import com.neilturner.aerialviews.utils.parallelForEachCompat
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

internal object MediaServiceHelper {
    suspend fun addMetadataToManifestVideos(
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

    fun addFilenameAsDescriptionToMedia(
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

    fun buildMediaList(providers: List<MediaProvider>): List<AerialMedia> {
        var media = CopyOnWriteArrayList<AerialMedia>()

        providers
            .filter { it.enabled == true }
            .parallelForEachCompat {
                runBlocking {
                    try {
                        media.addAll(it.fetchMedia())
                    } catch (ex: Exception) {
                        Timber.e(ex, "Exception while fetching media from ${it.type}")
                        FirebaseHelper.logExceptionIfRecent(ex)
                    }
                }
            }
        return media
    }
}
