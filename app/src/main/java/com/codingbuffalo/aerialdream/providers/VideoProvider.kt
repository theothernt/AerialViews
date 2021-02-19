package com.codingbuffalo.aerialdream.providers

import android.content.Context
import androidx.annotation.RawRes
import com.codingbuffalo.aerialdream.models.videos.Video
import com.google.gson.Gson
import java.util.*

abstract class VideoProvider {
    abstract fun fetchVideos(context: Context): List<Video>

    fun <T> parseJson(context: Context, @RawRes res: Int, tClass: Class<T>?): T {
        val `is` = context.resources.openRawResource(res)
        val json = Scanner(`is`).useDelimiter("\\A").next()
        return jsonParser.fromJson(json, tClass)
    }

    companion object {
        private val jsonParser = Gson()
    }
}