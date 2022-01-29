package com.neilturner.aerialviews.utils

import android.content.Context
import androidx.annotation.RawRes
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.neilturner.aerialviews.models.videos.Apple2018Video
import java.util.Scanner

object JsonHelper {

    private val jsonParser = Gson()

    fun <T> parseJson(context: Context, @RawRes res: Int, tClass: Class<T>?): T {
        val stream = context.resources.openRawResource(res)
        val json = Scanner(stream).useDelimiter("\\A").next()
        return jsonParser.fromJson(json, tClass)
    }

    fun parseJsonMap(context: Context, @RawRes res: Int): Map<String, String> {
        val stream = context.resources.openRawResource(res)
        val json = Scanner(stream).useDelimiter("\\A").next()
        return jsonParser.fromJson(json, object : TypeToken<Map<String, String>>() {}.type)
    }

    class Apple2018Videos {
        val assets: List<Apple2018Video>? = null
    }
}
