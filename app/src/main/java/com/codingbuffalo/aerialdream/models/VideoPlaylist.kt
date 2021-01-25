package com.codingbuffalo.aerialdream.models

import android.net.Uri
import com.codingbuffalo.aerialdream.models.videos.SimpleVideo
import com.codingbuffalo.aerialdream.models.videos.Video
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