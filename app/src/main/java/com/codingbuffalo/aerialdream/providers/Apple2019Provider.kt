package com.codingbuffalo.aerialdream.providers

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.annotation.RawRes
import com.codingbuffalo.aerialdream.R
import com.codingbuffalo.aerialdream.models.VideoQuality
import com.codingbuffalo.aerialdream.models.VideoSource
import com.codingbuffalo.aerialdream.models.videos.AerialVideo
import com.codingbuffalo.aerialdream.models.videos.Apple2019Video
import com.codingbuffalo.aerialdream.utils.FileHelper
import com.google.gson.Gson
import java.util.*

class Apple2019Provider(context: Context) : VideoProvider(context) {

    override fun fetchVideos(): List<AerialVideo> {
        val quality = VideoQuality.VIDEO_1080_H264
        val source = VideoSource.REMOTE
        val videos = mutableListOf<AerialVideo>()

        // tvOS13 videos
        val wrapperOS13 = parseJson(context, R.raw.tvos13, Wrapper::class.java)
        wrapperOS13.assets?.forEach {
            videos.add(AerialVideo(it.uri(quality), it.location))
        }

        // Older videos missing/removed from tvOS13 feed
//        val wrapperLegacy = parseJson(context, R.raw.legacy, Wrapper::class.java)
//        wrapperLegacy.assets?.forEach {
//            videos.add(AerialVideo(it.uri(quality), it.location))
//        }

        if (source == VideoSource.REMOTE) {
            Log.i(TAG, "${source.name} vids: ${videos.size}")
            return videos
        }

        val result = compareToLocalVideos(videos)

        if (source == VideoSource.LOCAL) {
            Log.i(TAG, "${source.name} vids: ${result.first.size}")
            return result.first // videos matched locally
        }

        Log.i(TAG, "${source.name} vids: ${result.first.size}, ${result.second.size}")
        return result.first + result.second // matched local videos, the rest are remote
    }

    private fun compareToLocalVideos(remoteVideos: List<AerialVideo>) : Pair<List<AerialVideo>,List<AerialVideo>> {

        val matched = mutableListOf<AerialVideo>()
        val unmatched = mutableListOf<AerialVideo>()
        val localVideos = FileHelper.findAllMedia(context)

        for (video in remoteVideos) {
            val remoteFilename = video.uri.lastPathSegment!!.toLowerCase(Locale.ROOT)

            val videoFound = localVideos.find {
                Uri.parse(it).lastPathSegment!!.toLowerCase(Locale.ROOT).contains(remoteFilename)
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
        private const val TAG = "Apple2019Provider"
    }
}