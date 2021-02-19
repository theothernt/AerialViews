package com.codingbuffalo.aerialdream.providers

import android.content.Context
import com.codingbuffalo.aerialdream.R
import com.codingbuffalo.aerialdream.models.videos.Apple2019Video
import com.codingbuffalo.aerialdream.models.videos.Video

class Apple2019Provider : VideoProvider() {
    override fun fetchVideos(context: Context): List<Video> {
        val wrapper = parseJson(context, R.raw.tvos13, Wrapper::class.java)
        return wrapper.assets!!
    }

    private class Wrapper {
        val assets: List<Apple2019Video>? = null
    }
}