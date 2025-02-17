package com.neilturner.aerialviews.providers

import android.content.Context
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.AerialMediaType
import com.neilturner.aerialviews.models.enums.ProviderSourceType
import com.neilturner.aerialviews.models.prefs.AppleVideoPrefs
import com.neilturner.aerialviews.models.videos.AerialMedia
import com.neilturner.aerialviews.utils.JsonHelper
import com.neilturner.aerialviews.utils.JsonHelper.parseJson
import com.neilturner.aerialviews.utils.JsonHelper.parseJsonMap
import timber.log.Timber

class AppleMediaProvider(
    context: Context,
    private val prefs: AppleVideoPrefs,
) : MediaProvider(context) {
    override val type = ProviderSourceType.REMOTE
    val metadata = mutableMapOf<String, Pair<String, Map<Int, String>>>()

    override val enabled: Boolean
        get() = prefs.enabled

    override suspend fun fetchMedia(): List<AerialMedia> = fetchAppleVideos().first

    override suspend fun fetchTest(): String = fetchAppleVideos().second

    override suspend fun fetchMetadata() = metadata

    private suspend fun fetchAppleVideos(): Pair<List<AerialMedia>, String> {
        val videos = mutableListOf<AerialMedia>()
        metadata.clear()
        val quality = prefs.quality
        val strings = parseJsonMap(context, R.raw.tvos15_strings)
        val wrapper = parseJson(context, R.raw.tvos15, JsonHelper.Apple2018Videos::class.java)
        wrapper.assets?.forEach {
            videos.add(
                AerialMedia(
                    it.uriAtQuality(quality),
                    type = AerialMediaType.VIDEO,
                ),
            )
            val data = Pair(it.description,
                it.pointsOfInterest.mapValues { poi ->
                    strings[poi.value] ?: it.description
                })
            it.allUrls().forEachIndexed { index, url ->
                metadata.put(url, data)
            }
        }

        Timber.i("${metadata.count()} metadata items found")
        Timber.i("${videos.count()} $quality videos found")
        return Pair(videos, "")
    }
}
