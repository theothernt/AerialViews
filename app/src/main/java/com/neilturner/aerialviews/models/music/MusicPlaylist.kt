package com.neilturner.aerialviews.models.music

class MusicPlaylist(
    val tracks: List<MusicTrack>,
    var shuffle: Boolean = false,
    var repeat: Boolean = false,
) {
    val size: Int = tracks.size
}
