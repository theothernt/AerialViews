package com.neilturner.aerialviews.models

import com.neilturner.aerialviews.models.videos.AerialVideo

class VideoPlaylist(private val videos: List<AerialVideo>) {
    private var position = -1

    fun nextVideo(): AerialVideo {
        position = calculateNext(++position)
        return videos[position]
    }

    fun previousVideo(): AerialVideo {
        position = calculateNext(--position)
        return videos[position]
    }

    private fun calculateNext(number: Int): Int {
        val next = if (number < 0) {
            videos.size + number
        } else {
            (number).rem(videos.size)
        }
        return next
    }
}
