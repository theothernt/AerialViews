package com.codingbuffalo.aerialdream.models

import android.net.Uri
import com.codingbuffalo.aerialdream.models.videos.AerialVideo

class VideoPlaylist(private val videos: MutableList<AerialVideo>) {
    private var position = 0

    val video: AerialVideo
        get() = if (videos.isNotEmpty()) {
            videos[position++ % videos.size]
        } else {
            AerialVideo(Uri.parse(""), "")
        }

    init {
        videos.shuffle()
    }
}