package com.neilturner.aerialviews.services

import com.neilturner.aerialviews.models.enums.DescriptionFilenameType
import com.neilturner.aerialviews.models.videos.AerialMedia
import com.neilturner.aerialviews.providers.MediaProvider
import com.neilturner.aerialviews.utils.FileHelper
import com.neilturner.aerialviews.utils.filenameWithoutExtension
import com.neilturner.aerialviews.utils.parallelForEach
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

internal object MediaServiceHelper {
    suspend fun addMetadataToManifestVideos(
        media: List<AerialMedia>,
        providers: List<MediaProvider>,
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

        media.parallelForEach { video ->
            val data = metadata[video.uri.filenameWithoutExtension.lowercase()]
            if (data != null) {
                video.metadata.shortDescription = data.first
                video.metadata.pointsOfInterest = data.second
                matched.add(video)
            } else {
                unmatched.add(video)
            }
        }

        return Pair(matched, unmatched)
    }

    suspend fun buildMediaList(providers: List<MediaProvider>): List<AerialMedia> {
        val media = CopyOnWriteArrayList<AerialMedia>()

        providers
            .filter { it.enabled }
            .parallelForEach {
                try {
                    it.prepare()
                    val providerMedia = it.fetchMedia()
                    media.addAll(providerMedia)
                } catch (ex: Exception) {
                    Timber.e(ex, "Exception while fetching media from ${it.type}")
                    // FirebaseHelper.logExceptionIfRecent(ex)
                }
            }
        return media
    }
}
