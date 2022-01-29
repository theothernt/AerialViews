package com.neilturner.aerialviews.providers

import android.content.Context
import android.util.Log
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.prefs.AppleVideoPrefs
import com.neilturner.aerialviews.models.videos.AerialVideo
import com.neilturner.aerialviews.utils.JsonHelper
import com.neilturner.aerialviews.utils.JsonHelper.parseJson
import com.neilturner.aerialviews.utils.JsonHelper.parseJsonMap

class AppleVideoProvider(context: Context, private val prefs: AppleVideoPrefs) : VideoProvider(context) {

    override fun fetchVideos(): List<AerialVideo> {
        val quality = prefs.quality
        val videos = mutableListOf<AerialVideo>()

        val strings = parseJsonMap(context, R.raw.tvos15_strings)
        // tvOS videos
        val wrapper = parseJson(context, R.raw.tvos15, JsonHelper.Apple2018Videos::class.java)
        wrapper.assets?.forEach {
            videos.add(
                AerialVideo(
                    it.uri(quality)!!, it.location,
                    it.pointsOfInterest.mapValues { poi ->
                        strings[poi.value] ?: it.location
                    }
                )
            )
        }

        Log.i(TAG, "tvOS: ${videos.count()} $quality videos found")
        return videos
    }

    companion object {
        private const val TAG = "AppleVideoProvider"
    }
}
