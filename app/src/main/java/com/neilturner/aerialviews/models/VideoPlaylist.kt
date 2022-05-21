package com.neilturner.aerialviews.models

import com.neilturner.aerialviews.models.videos.AerialVideo

class VideoPlaylist(private val _videos: List<AerialVideo>) {
    private var position = -1

    val size: Int = _videos.size

    fun nextVideo(): AerialVideo {
        position = calculateNext(++position)
        return _videos[position]
    }

    fun previousVideo(): AerialVideo {
        position = calculateNext(--position)
        return _videos[position]
    }

    private fun calculateNext(number: Int): Int {
        val next = if (number < 0) {
            _videos.size + number
        } else {
            (number).rem(_videos.size)
        }
        return next
    }
}
