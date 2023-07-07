package com.neilturner.aerialviews.providers

import android.content.Context
import android.util.Log
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.prefs.Comm2VideoPrefs
import com.neilturner.aerialviews.models.videos.AerialVideo
import com.neilturner.aerialviews.models.videos.VideoMetadata
import com.neilturner.aerialviews.utils.JsonHelper
import com.neilturner.aerialviews.utils.JsonHelper.parseJson
import com.neilturner.aerialviews.utils.JsonHelper.parseJsonMap

class Comm2VideoProvider(context: Context, private val prefs: Comm2VideoPrefs) : VideoProvider(context) {

    override fun fetchVideos(): List<AerialVideo> {
        return fetchCommunityVideos().first
    }

    override fun fetchTest(): String {
        return fetchCommunityVideos().second
    }

    override fun fetchMetadata(): List<VideoMetadata> {
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

    private fun fetchCommunityVideos(): Pair<List<AerialVideo>, String> {
        val videos = mutableListOf<AerialVideo>()
        val quality = prefs.quality
        val strings = parseJsonMap(context, R.raw.comm2_strings)
        val wrapper = parseJson(context, R.raw.comm2, JsonHelper.Comm2Videos::class.java)
        wrapper.assets?.forEach {
            videos.add(
                AerialVideo(
                    it.uriAtQuality(quality),
                    it.location,
                    it.pointsOfInterest.mapValues { poi ->
                        strings[poi.value] ?: it.location
                    }
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
