package com.codingbuffalo.aerialdream.data

import android.content.Context
import androidx.annotation.RawRes
import com.google.gson.Gson
import java.io.IOException
import java.util.*

abstract class VideoRepository {
    @Throws(IOException::class)
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