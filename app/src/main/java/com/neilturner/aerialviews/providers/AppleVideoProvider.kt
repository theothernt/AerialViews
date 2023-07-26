package com.neilturner.aerialviews.providers

import android.content.Context
import android.util.Log
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.prefs.AppleVideoPrefs
import com.neilturner.aerialviews.models.videos.AerialVideo
import com.neilturner.aerialviews.models.videos.VideoMetadata
import com.neilturner.aerialviews.utils.JsonHelper
import com.neilturner.aerialviews.utils.JsonHelper.parseJson
import com.neilturner.aerialviews.utils.JsonHelper.parseJsonMap

class AppleVideoProvider(context: Context, private val prefs: AppleVideoPrefs) : VideoProvider(context) {

    override val enabled: Boolean
        get() = prefs.enabled

    override fun fetchVideos(): List<AerialVideo> {
        return fetchAppleVideos().first
    }

    override fun fetchTest(): String {
        return fetchAppleVideos().second
    }

    override fun fetchMetadata(): List<VideoMetadata> {
        val metadata = mutableListOf<VideoMetadata>()
        val strings = parseJsonMap(context, R.raw.tvos15_strings)
        val wrapper = parseJson(context, R.raw.tvos15, JsonHelper.Apple2018Videos::class.java)
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

    private fun fetchAppleVideos(): Pair<List<AerialVideo>, String> {
        val videos = mutableListOf<AerialVideo>()
        val quality = prefs.quality
        val wrapper = parseJson(context, R.raw.tvos15, JsonHelper.Apple2018Videos::class.java)
        wrapper.assets?.forEach {
            videos.add(
                AerialVideo(
                    it.uriAtQuality(quality)
                )
            )
        }

        Log.i(TAG, "${videos.count()} $quality videos found")
        return Pair(videos, "")
    }

    companion object {
        private const val TAG = "AppleVideoProvider"
    }
}
