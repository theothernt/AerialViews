package com.neilturner.aerialviews.providers

import android.content.Context
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.AerialMediaSource
import com.neilturner.aerialviews.models.enums.AerialMediaType
import com.neilturner.aerialviews.models.enums.ProviderSourceType
import com.neilturner.aerialviews.models.enums.SceneType
import com.neilturner.aerialviews.models.enums.TimeOfDay
import com.neilturner.aerialviews.models.prefs.Comm2VideoPrefs
import com.neilturner.aerialviews.models.videos.AerialMedia
import com.neilturner.aerialviews.models.videos.Comm2Videos
import com.neilturner.aerialviews.utils.JsonHelper.parseJson
import com.neilturner.aerialviews.utils.JsonHelper.parseJsonMap
import timber.log.Timber

class Comm2MediaProvider(
    context: Context,
    private val prefs: Comm2VideoPrefs,
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
        val strings = parseJsonMap(context, R.raw.comm2_strings)
        val wrapper = parseJson<Comm2Videos>(context, R.raw.comm2)

        wrapper.assets?.forEach { asset ->
            val timeOfDay = TimeOfDay.fromString(asset.timeOfDay)
            val scene = SceneType.fromString(asset.scene)

            val timeOfDayMatches = prefs.timeOfDay.contains(timeOfDay.toString())
            val sceneMatches = prefs.scene.contains(scene.toString())

            if (timeOfDayMatches && sceneMatches) {
                videos.add(
                    AerialMedia(
                        asset.uriAtQuality(quality),
                        type = AerialMediaType.VIDEO,
                        source = AerialMediaSource.COMM2,
                        timeOfDay = timeOfDay,
                        scene = scene
                    ),
                )
            }
            val data =
                Pair(
                    asset.description,
                    asset.pointsOfInterest.mapValues { poi ->
                        strings[poi.value] ?: asset.description
                    },
                )
            asset.allUrls().forEachIndexed { index, url ->
                metadata.put(url, data)
            }
        }

        Timber.i("${metadata.count()} metadata items found")
        Timber.i("${videos.count()} $quality videos found")
    }
}
