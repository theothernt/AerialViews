package com.neilturner.aerialviews.providers

import android.content.Context
import android.util.Log
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.MediaItemType
import com.neilturner.aerialviews.models.enums.ProviderType
import com.neilturner.aerialviews.models.prefs.Comm2VideoPrefs
import com.neilturner.aerialviews.models.videos.AerialMedia
import com.neilturner.aerialviews.models.videos.VideoMetadata
import com.neilturner.aerialviews.utils.JsonHelper
import com.neilturner.aerialviews.utils.JsonHelper.parseJson
import com.neilturner.aerialviews.utils.JsonHelper.parseJsonMap

class Comm2MediaProvider(context: Context, private val prefs: Comm2VideoPrefs) : MediaProvider(context) {

    override val type = ProviderType.REMOTE

    override val enabled: Boolean
        get() = prefs.enabled

    override suspend fun fetchMedia(): List<AerialMedia> {
        return fetchCommunityVideos().first
    }

    override suspend fun fetchTest(): String {
        return fetchCommunityVideos().second
    }

    override suspend fun fetchMetadata(): List<VideoMetadata> {
        val metadata = mutableListOf<VideoMetadata>()
        val strings = parseJsonMap(context, R.raw.comm2_strings)
        val wrapper = parseJson(context, R.raw.comm2, JsonHelper.Comm2Videos::class.java)
        wrapper.assets?.forEach {
            val video = VideoMetadata(
                it.allUrls(),
                it.location,
                it.pointsOfInterest.mapValues { poi ->
                    strings[poi.value] ?: it.location
                }
            )
            metadata.add(video)
        }
        return metadata
    }

    private suspend fun fetchCommunityVideos(): Pair<List<AerialMedia>, String> {
        val videos = mutableListOf<AerialMedia>()
        val quality = prefs.quality
        val wrapper = parseJson(context, R.raw.comm2, JsonHelper.Comm2Videos::class.java)
        wrapper.assets?.forEach {
            videos.add(
                AerialMedia(
                    it.uriAtQuality(quality),
                    type = MediaItemType.VIDEO
                )
            )
        }

        Log.i(TAG, "${videos.count()} $quality videos found")
        return Pair(videos, "")
    }

    companion object {
        private const val TAG = "Comm2VideoProvider"
    }
}
