package com.codingbuffalo.aerialdream.data

import android.net.Uri
import java.util.*

class VideoPlaylist(private val videos: List<Video?>) {
    private var position = 0
    val video: Video?
        get() = if (videos.isNotEmpty()) {
            videos[position++ % videos.size]
        } else {
            SimpleVideo(Uri.parse(""), "")
        }

    init {
        Collections.shuffle(videos)
    }
}