package com.neilturner.aerialviews.providers

import android.content.Context
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.AerialMediaType
import com.neilturner.aerialviews.models.enums.ProviderSourceType
import com.neilturner.aerialviews.models.prefs.Comm1VideoPrefs
import com.neilturner.aerialviews.models.videos.AerialMedia
import com.neilturner.aerialviews.models.videos.VideoMetadata
import com.neilturner.aerialviews.utils.JsonHelper
import com.neilturner.aerialviews.utils.JsonHelper.parseJson
import com.neilturner.aerialviews.utils.JsonHelper.parseJsonMap
import timber.log.Timber

class Comm1MediaProvider(
    context: Context,
    private val prefs: Comm1VideoPrefs,
) : MediaProvider(context) {
    override val type = ProviderSourceType.REMOTE
    val metadata = mutableListOf<VideoMetadata>()

    override val enabled: Boolean
        get() = prefs.enabled

    override suspend fun fetchMedia(): List<AerialMedia> = fetchCommunityVideos().first

    override suspend fun fetchTest(): String = fetchCommunityVideos().second

    override suspend fun fetchMetadata() = metadata

    private suspend fun fetchCommunityVideos(): Pair<List<AerialMedia>, String> {
        val videos = mutableListOf<AerialMedia>()
        metadata.clear()
        val quality = prefs.quality
        val strings = parseJsonMap(context, R.raw.comm1_strings)
        val wrapper = parseJson(context, R.raw.comm1, JsonHelper.Comm1Videos::class.java)
        wrapper.assets?.forEach {
            videos.add(
                AerialMedia(
                    it.uriAtQuality(quality),
                    type = AerialMediaType.VIDEO,
                ),
            )
            metadata.add(VideoMetadata(
                    it.allUrls(),
                    it.description,
                    it.pointsOfInterest.mapValues { poi ->
                        strings[poi.value] ?: it.description
                    },
                )
            )
        }

        Timber.i("${videos.count()} $quality videos found")
        return Pair(videos, "")
    }
}
