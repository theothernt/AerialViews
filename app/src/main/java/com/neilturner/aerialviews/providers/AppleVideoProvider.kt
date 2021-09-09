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
        val location = prefs.location
        val videos = mutableListOf<AerialVideo>()

        Log.i(TAG, "$location, $quality")

        // tvOS13 videos
        val wrapperOS13 = parseJson(context, R.raw.tvos13, Wrapper::class.java)
        wrapperOS13.assets?.forEach {
            videos.add(AerialVideo(it.uri(quality), it.location))
        }

        Log.i(TAG, "tvOS13: ${videos.count()} videos found")

        // Older videos missing/removed from tvOS13 feed
//        val wrapperLegacy = parseJson(context, R.raw.legacy, Wrapper::class.java)
//        wrapperLegacy.assets?.forEach {
//            videos.add(AerialVideo(it.uri(quality), it.location))
//        }

        if (location == AppleVideoLocation.REMOTE) {
            Log.i(TAG, "${location.name} videos: ${videos.size}")
            return videos
        }

        val result = try {
            compareToLocalVideos(videos)
        } catch(ex: Exception) {
            Log.e(TAG, ex.message!!)
            Pair(emptyList(), emptyList())
        }

        if (location == AppleVideoLocation.LOCAL) {
            Log.i(TAG, "${location.name} videos: ${result.first.size}")
            return result.first // videos matched locally
        }

        Log.i(TAG, "${location.name} videos: ${result.first.size}, ${result.second.size}")
        return result.first + result.second // matched local videos, the rest are remote
    }

    private fun compareToLocalVideos(remoteVideos: List<AerialVideo>) : Pair<List<AerialVideo>,List<AerialVideo>> {
        val matched = mutableListOf<AerialVideo>()
        val unmatched = mutableListOf<AerialVideo>()
        val localVideos = FileHelper.findAllMedia(context)

        for (video in remoteVideos) {
            val remoteFilename = video.uri.lastPathSegment!!.lowercase()

            val videoFound = localVideos.find {
                val localFilename = Uri.parse(it).lastPathSegment!!.lowercase()
                localFilename.contains(remoteFilename)
            }

            if (videoFound != null) {
                matched.add(AerialVideo(Uri.parse(videoFound), video.location))
            } else {
                unmatched.add(AerialVideo(video.uri, video.location))
            }
        }

        return Pair(matched, unmatched)
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