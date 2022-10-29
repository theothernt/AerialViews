package com.neilturner.aerialviews.providers

import android.content.Context
import android.util.Log
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.prefs.AerialShotsVideoPrefs
import com.neilturner.aerialviews.models.videos.AerialVideo
import com.neilturner.aerialviews.utils.JsonHelper
import com.neilturner.aerialviews.utils.JsonHelper.parseJson

class AerialShotsVideoProvider(context: Context, private val prefs: AerialShotsVideoPrefs) : VideoProvider(context) {

    override fun fetchVideos(): List<AerialVideo> {
        val quality = prefs.quality
        val videos = mutableListOf<AerialVideo>()
        val wrapper = parseJson(context, R.raw.aerialshots, JsonHelper.AerialShotsVideos::class.java)
        wrapper.assets?.forEach {
            videos.add(
                AerialVideo(
                    it.uri(quality)!!,
                    it.location,
                    it.pointsOfInterest
                )
            )
        }

        Log.i(TAG, "Aerial Shots: ${videos.count()} $quality videos found")
        return videos
    }

    companion object {
        private const val TAG = "ShotsVideoProvider"
    }
}
