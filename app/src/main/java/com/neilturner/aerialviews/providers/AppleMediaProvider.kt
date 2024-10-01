package com.neilturner.aerialviews.providers

import android.content.Context
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.AerialMediaType
import com.neilturner.aerialviews.models.enums.ProviderSourceType
import com.neilturner.aerialviews.models.prefs.AppleVideoPrefs
import com.neilturner.aerialviews.models.videos.AerialMedia
import com.neilturner.aerialviews.models.videos.VideoMetadata
import com.neilturner.aerialviews.utils.JsonHelper
import com.neilturner.aerialviews.utils.JsonHelper.parseJson
import com.neilturner.aerialviews.utils.JsonHelper.parseJsonMap
import timber.log.Timber

class AppleMediaProvider(
    context: Context,
    private val prefs: AppleVideoPrefs,
) : MediaProvider(context) {
    override val type = ProviderSourceType.REMOTE

    override val enabled: Boolean
        get() = prefs.enabled

    override suspend fun fetchMedia(): List<AerialMedia> = fetchAppleVideos().first

    override suspend fun fetchTest(): String = fetchAppleVideos().second

    override suspend fun fetchMetadata(): List<VideoMetadata> {
        val metadata = mutableListOf<VideoMetadata>()
        val strings = parseJsonMap(context, R.raw.tvos15_strings)
        val wrapper = parseJson(context, R.raw.tvos15, JsonHelper.Apple2018Videos::class.java)
        wrapper.assets?.forEach {
            val video =
                VideoMetadata(
                    it.allUrls(),
                    it.description,
                    it.pointsOfInterest.mapValues { poi ->
                        strings[poi.value] ?: it.description
                    },
                )
            metadata.add(video)
        }
        return metadata
    }

    private suspend fun fetchAppleVideos(): Pair<List<AerialMedia>, String> {
        val videos = mutableListOf<AerialMedia>()
        val quality = prefs.quality
        val wrapper = parseJson(context, R.raw.tvos15, JsonHelper.Apple2018Videos::class.java)
        wrapper.assets?.forEach {
            videos.add(
                AerialMedia(
                    it.uriAtQuality(quality),
                    type = AerialMediaType.VIDEO,
                ),
            )
        }

        Timber.i("${videos.count()} $quality videos found")
        return Pair(videos, "")
    }
}
