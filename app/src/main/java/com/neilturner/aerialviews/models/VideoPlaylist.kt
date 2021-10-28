package com.neilturner.aerialviews.models

import com.neilturner.aerialviews.models.videos.AerialVideo

class VideoPlaylist(private val videos: List<AerialVideo>) {
    private var position = 0

    fun nextVideo(): AerialVideo {
        return videos[position++ % videos.size]
    }

    fun previousVideo(): AerialVideo {
        return videos[position-- % videos.size]
    }
}