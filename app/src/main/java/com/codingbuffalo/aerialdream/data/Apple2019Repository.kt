package com.codingbuffalo.aerialdream.data

import android.content.Context
import com.codingbuffalo.aerialdream.R

class Apple2019Repository : VideoRepository() {
    override fun fetchVideos(context: Context): List<Video> {
        val wrapper = parseJson(context, R.raw.tvos13, Wrapper::class.java)
        return wrapper.assets!!
    }

    private class Wrapper {
        val assets: List<Apple2019Video>? = null
    }
}