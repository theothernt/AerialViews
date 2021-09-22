package com.neilturner.aerialviews.providers

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.RawRes
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.AppleVideoLocation
import com.neilturner.aerialviews.models.prefs.AppleVideoPrefs
import com.neilturner.aerialviews.models.videos.AerialVideo
import com.neilturner.aerialviews.models.videos.Apple2019Video
import com.neilturner.aerialviews.utils.FileHelper
import com.google.gson.Gson
import java.util.*

class AppleVideoProvider(context: Context, private val prefs: AppleVideoPrefs) : VideoProvider(context) {

    override fun fetchVideos(): List<AerialVideo> {
        val quality = prefs.quality
        val videos = mutableListOf<AerialVideo>()

        // tvOS13 videos
        val wrapperOS13 = parseJson(context, R.raw.tvos13, Wrapper::class.java)
        wrapperOS13.assets?.forEach {
            videos.add(AerialVideo(it.uri(quality), it.location))
        }

        Log.i(TAG, "tvOS13: ${videos.count()} $quality videos found")
        return videos
    }

    private fun <T> parseJson(context: Context, @RawRes res: Int, tClass: Class<T>?): T {
        val stream = context.resources.openRawResource(res)
        val json = Scanner(stream).useDelimiter("\\A").next()
        return jsonParser.fromJson(json, tClass)
    }

    private class Wrapper {
        val assets: List<Apple2019Video>? = null
    }

    companion object {
        private val jsonParser = Gson()
        private const val TAG = "AppleVideoProvider"
    }
}