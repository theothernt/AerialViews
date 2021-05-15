package com.codingbuffalo.aerialdream.models

import com.codingbuffalo.aerialdream.models.videos.AerialVideo

class VideoPlaylist(private val videos: List<AerialVideo>) {
    private var position = 0

    fun nextVideo(): AerialVideo {
        return videos[position++ % videos.size]
    }

    //fun previousVideo(): AerialVideo {}
}