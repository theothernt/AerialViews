package com.neilturner.aerialviews.models

import com.neilturner.aerialviews.models.videos.AerialMedia

class MediaPlaylist(
    private val _videos: List<AerialMedia>,
) {
    private var position = -1

    val size: Int = _videos.size

    fun nextItem(): AerialMedia {
        position = calculateNext(++position)
        return _videos[position]
    }

    fun previousItem(): AerialMedia {
        position = calculateNext(--position)
        return _videos[position]
    }
    
    fun peekNextItem(): AerialMedia? {
        if (_videos.isEmpty()) return null
        val nextPosition = calculateNext(position + 1)
        return _videos[nextPosition]
    }

    private fun calculateNext(number: Int): Int {
        val next =
            if (number < 0) {
                _videos.size + number
            } else {
                (number).rem(_videos.size)
            }
        return next
    }
}
