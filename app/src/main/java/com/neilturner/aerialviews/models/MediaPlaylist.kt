package com.neilturner.aerialviews.models

import com.neilturner.aerialviews.models.videos.AerialMedia

class MediaPlaylist(
    private val _videos: List<AerialMedia>,
    startPosition: Int = -1,
) {
    private var position = startPosition
    private var _hasReachedEnd = false

    val size: Int = _videos.size
    val currentPosition: Int get() = position
    val hasReachedEnd: Boolean get() = _hasReachedEnd

    fun nextItem(): AerialMedia {
        position = calculateNext(++position)
        if (position == 0 && size > 0) _hasReachedEnd = true
        return _videos[position]
    }

    fun previousItem(): AerialMedia {
        position = calculateNext(--position)
        return _videos[position]
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
