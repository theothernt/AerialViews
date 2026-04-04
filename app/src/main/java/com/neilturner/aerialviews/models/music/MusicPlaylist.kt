package com.neilturner.aerialviews.models.music

class MusicPlaylist(
    private val _tracks: List<MusicTrack>,
    var shuffle: Boolean = false,
    var repeat: Boolean = false,
) {
    private var position = -1

    val size: Int = _tracks.size

    fun nextTrack(): MusicTrack {
        position = calculateNext(++position)
        return _tracks[position]
    }

    fun previousTrack(): MusicTrack {
        position = calculateNext(--position)
        return _tracks[position]
    }

    private fun calculateNext(number: Int): Int {
        val next =
            if (number < 0) {
                _tracks.size + number
            } else {
                (number).rem(_tracks.size)
            }
        return next
    }
}
