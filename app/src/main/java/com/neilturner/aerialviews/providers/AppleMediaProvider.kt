package com.neilturner.aerialviews.providers

import android.content.Context
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.AerialMediaSource
import com.neilturner.aerialviews.models.enums.AerialMediaType
import com.neilturner.aerialviews.models.enums.ProviderSourceType
import com.neilturner.aerialviews.models.enums.SceneType
import com.neilturner.aerialviews.models.enums.TimeOfDay
import com.neilturner.aerialviews.models.prefs.AppleVideoPrefs
import com.neilturner.aerialviews.models.videos.AerialMedia
import com.neilturner.aerialviews.models.videos.Apple2018Videos
import com.neilturner.aerialviews.utils.JsonHelper.parseJson
import com.neilturner.aerialviews.utils.JsonHelper.parseJsonMap
import timber.log.Timber

class AppleMediaProvider(
    context: Context,
    private val prefs: AppleVideoPrefs,
) : MediaProvider(context) {
    override val type = ProviderSourceType.REMOTE
    val metadata = mutableMapOf<String, Pair<String, Map<Int, String>>>()
    val videos = mutableListOf<AerialMedia>()

    override val enabled: Boolean
        get() = prefs.enabled

    override suspend fun fetchTest(): String = ""

    override suspend fun fetchMedia(): List<AerialMedia> {
        if (metadata.isEmpty()) buildVideoAndMetadata()
        return videos
    }

    override suspend fun fetchMetadata(): MutableMap<String, Pair<String, Map<Int, String>>> {
        if (metadata.isEmpty()) buildVideoAndMetadata()
        return metadata
    }

    private suspend fun buildVideoAndMetadata() {
        val quality = prefs.quality
        val strings = parseJsonMap(context, R.raw.tvos15_strings)
        val wrapper = parseJson<Apple2018Videos>(context, R.raw.tvos15)

        wrapper.assets?.forEach {
            videos.add(
                AerialMedia(
                    it.uriAtQuality(quality),
                    type = AerialMediaType.VIDEO,
                    source = AerialMediaSource.APPLE,
                    timeOfDay = TimeOfDay.fromString(it.timeOfDay),
                    scene = SceneType.fromString(it.scene)
                ),
            )
            val data =
                Pair(
                    it.description,
                    it.pointsOfInterest.mapValues { poi ->
                        strings[poi.value] ?: it.description
                    },
                )
            it.allUrls().forEachIndexed { index, url ->
                metadata.put(url, data)
            }
        }

        Timber.i("${metadata.count()} metadata items found")
        Timber.i("${videos.count()} $quality videos found")
    }
}
